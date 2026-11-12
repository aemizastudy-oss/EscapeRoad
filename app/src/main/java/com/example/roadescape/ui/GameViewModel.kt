package com.example.roadescape.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roadescape.AppContainer
import com.example.roadescape.BuildConfig
import com.example.roadescape.data.GameStorageException
import com.example.roadescape.data.StorageFailure
import com.example.roadescape.game.*
import com.example.roadescape.model.*
import com.example.roadescape.platform.sdk.PleosSdkStatus
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameUiError(val contentError: Boolean, val reason: String, val detail: String?)
data class GameUiState(
    val content: GamePackage? = null, val session: GameSession? = null,
    val legacyPreview: GamePackage? = null,
    val loading: Boolean = true, val saving: Boolean = false, val error: GameUiError? = null,
    val parking: ParkingState = ParkingState.UNKNOWN, val foreground: Boolean = false,
    val feedback: GameFeedback? = null, val rejectedGate: GateReason? = null,
    val mode: TourMode = if(BuildConfig.DEBUG)TourMode.MOCK else TourMode.LIVE,
    val safety: TourSafety = TourSafety(false,false,false,false),
    val sdk: PleosSdkStatus = PleosSdkStatus(),
    val tourGate: TourGate = TourGate.LOCATION_MISSING,
    val tourStartGate: TourGate = TourGate.LOCATION_MISSING,
    val tourElapsedMillis: Long = 0, val tourRecord: TourStartRecord? = null,
    val tourError: String? = null, val sdkMessage: String? = null, val voiceDraft: String? = null,
) {
    val environment get() = PlayEnvironment(foreground,parking,!loading&&error==null,saving,
        mode=mode,carUxAllowed=safety.carUxAllowed,stationary=safety.stationary,
        tourGate=if(tourError==null&&tourGate==TourGate.READY)GateReason.READY else GateReason.TOUR_REQUIRED,
        arrivalVerified=tourError==null&&tourStartGate==TourGate.READY)
    // Parked details can be viewed before the current puzzle's fifteen-minute gate opens.
    val canReadContent get() = foreground&&parking==ParkingState.PARKED&&safety.stationary&&
        safety.carUxAllowed&&error==null&&!loading&&(session==null||session.sourceMode==mode)
}

class GameViewModel(val container:AppContainer):ViewModel() {
    private sealed interface Event {
        data object Load:Event
        data class NewGame(val packageId:String?,val mode:TourMode?):Event
        data class Command(val command:GameCommand,val foreground:Boolean,val foregroundRevision:Long,val revision:Long):Event
        data class StartTour(val key:TourKey,val revision:Long,val pressedAt:TourClock):Event
        data class DebugElapsed(val key:TourKey):Event
    }
    private val mutable=MutableStateFlow(GameUiState())
    val state=mutable.asStateFlow()
    private val events=Channel<Event>(Channel.UNLIMITED)
    private val destinations=Channel<String>(Channel.BUFFERED)
    val navigation=destinations.receiveAsFlow()
    private var foregroundRevision=0L
    private var mockRevision=0L
    private var mockLocation:TourLocation?=null
    private var mockLocationCheckpoint:String?=null
    private var tours=TourState()
    private var voiceGeneration=0L
    private val runtime get()=container.sdkRuntime

    init {
        viewModelScope.launch { for(e in events)when(e){
            Event.Load->load();is Event.NewGame->newGame(e);is Event.Command->process(e)
            is Event.StartTour->beginTour(e);is Event.DebugElapsed->debugElapsed(e)
        }}
        viewModelScope.launch { container.sources.parking.snapshot.collect { refresh() } }
        viewModelScope.launch { runtime.safety.collect { refresh() } }
        viewModelScope.launch { runtime.locations.collect { refresh() } }
        viewModelScope.launch { runtime.status.collect { status->mutable.update{it.copy(sdk=status)};refresh() } }
        viewModelScope.launch { container.sources.arrival.arrivals.collect { arrival->
            if(mutable.value.mode==TourMode.MOCK) {
                if(mutable.value.content?.requiresTour==true){
                    val key=currentKey()
                    if(arrival.sessionId==key?.sessionId&&arrival.checkpointId==key.checkpointId)simulateTourLocation(true)
                }
                else enqueue(GameCommand.Arrive(arrival.sessionId,arrival.checkpointId,arrival.arrivedAt))
            }
        }}
        viewModelScope.launch { while(true){delay(1_000);if(mutable.value.foreground){
            if(BuildConfig.DEBUG&&mutable.value.mode==TourMode.MOCK)mockLocation=mockLocation?.copy(
                timestampEpochMillis=System.currentTimeMillis(),receivedElapsedRealtimeMillis=SystemClock.elapsedRealtime())
            refresh()
        }}}
        events.trySend(Event.Load)
    }
    private fun clock()=TourClock(System.currentTimeMillis(),SystemClock.elapsedRealtime(),
        "boot:"+Settings.Global.getInt(container.appContext.contentResolver,Settings.Global.BOOT_COUNT,-1))
    private fun inputRevision()=if(mutable.value.mode==TourMode.LIVE)runtime.revision
        else container.sources.parking.snapshot.value.revision*1_000_003L+mockRevision
    private fun currentKey():TourKey?{
        val s=mutable.value;val session=s.session?:return null
        val cp=s.content?.let{GameController.currentCheckpoint(it,session)}?:return null
        return TourKey(session.sessionId,cp.id,s.mode)
    }
    private fun safety():TourSafety {
        val s=mutable.value
        return if(s.mode==TourMode.LIVE)runtime.safety.value.copy(foreground=s.foreground&&runtime.safety.value.foreground)
        else {val p=container.sources.parking.snapshot.value.state==ParkingState.PARKED;TourSafety(s.foreground,p,p,p)}
    }
    private fun location()=if(mutable.value.mode==TourMode.LIVE)runtime.locations.value
        else mockLocation.takeIf{mockLocationCheckpoint==currentKey()?.checkpointId}
    private fun refresh(){
        val s=mutable.value;val safe=safety()
        val parking=if(s.mode==TourMode.MOCK)container.sources.parking.snapshot.value.state
            else if(safe.parked&&safe.stationary)ParkingState.PARKED
            else if(runtime.status.value.carSafety.availability==com.example.roadescape.platform.sdk.SdkAvailability.READY&&!safe.stationary)ParkingState.DRIVING
            else ParkingState.UNKNOWN
        val key=currentKey();val target=key?.let{TourCatalog.find(it.checkpointId)}
        val record=key?.let{tours.find(it)};val now=clock()
        val start=target?.let{TourPolicy.startGate(s.mode,it,location(),safe,now)}?:TourGate.COORDINATES_NOT_CONFIGURED
        val gate=target?.let{TourPolicy.unlockGate(record,s.mode,it,location(),safe,now)}?:TourGate.COORDINATES_NOT_CONFIGURED
        val elapsed=record?.let{(TourPolicy.elapsed(it,now)as?TourTimeResult.Valid)?.elapsedMillis}?:0L
        val lost=!safe.foreground||!safe.parked||!safe.stationary||!safe.carUxAllowed||
            (s.content?.requiresTour==true&&gate!=TourGate.READY)
        if(lost){runtime.stopAudio();voiceGeneration++}
        mutable.update{it.copy(parking=parking,safety=safe,tourStartGate=start,tourGate=gate,
            tourRecord=record,tourElapsedMillis=elapsed,voiceDraft=if(lost)null else it.voiceDraft)}
    }
    fun setForeground(foreground:Boolean){
        foregroundRevision++
        if(!foreground){container.sources.parking.invalidate();mockLocation=null;mockRevision++;runtime.stopAudio()}
        mutable.update{it.copy(foreground=foreground)}
        if(mutable.value.mode==TourMode.LIVE){runtime.setForeground(foreground);if(foreground)runtime.start()else runtime.stop()}
        refresh()
    }
    fun retry(){if(!mutable.value.saving)events.trySend(Event.Load)}
    fun newGameConfirmed(packageId:String?=null,mode:TourMode?=null){
        if(!mutable.value.loading&&!mutable.value.saving)events.trySend(Event.NewGame(packageId,mode))
    }
    fun startSuwonTour(mode:TourMode)=newGameConfirmed(TourCatalog.PACKAGE_ID,mode)
    fun command(command:GameCommand){if(!mutable.value.loading&&!mutable.value.saving)enqueue(command)}
    private fun enqueue(command:GameCommand){events.trySend(Event.Command(command,mutable.value.foreground,foregroundRevision,inputRevision()))}
    fun startTour(){currentKey()?.let{events.trySend(Event.StartTour(it,inputRevision(),clock()))}}
    fun debugTourElapsed(){if(BuildConfig.DEBUG)currentKey()?.let{events.trySend(Event.DebugElapsed(it))}}
    fun simulateTourLocation(inside:Boolean){
        if(!BuildConfig.DEBUG||mutable.value.mode!=TourMode.MOCK)return
        mockRevision++;mockLocationCheckpoint=currentKey()?.checkpointId;mockLocation=TourLocation(TourMode.MOCK,accuracyMeters=1.0,
            timestampEpochMillis=System.currentTimeMillis(),receivedElapsedRealtimeMillis=SystemClock.elapsedRealtime(),
            simulatedInsideRadius=inside);refresh()
    }
    private suspend fun load(){
        mutable.update{it.copy(loading=true,error=null,feedback=null)}
        val session=try{container.gameRepository.load()}catch(e:Exception){
            if(e is CancellationException)throw e
            val preview=runCatching{container.packageRepository.load()}.getOrNull()
            mutable.update{it.copy(content=preview,legacyPreview=preview)};storageError(e);return
        }
        val content=try{container.packageRepository.load(session?.packageId)}catch(e:Exception){
            if(e is CancellationException)throw e
            mutable.update{it.copy(loading=false,error=GameUiError(true,"CONTENT",e.message))};return
        }
        try{
            if(session!=null)validate(content,session)
            val preview=if(content.id=="demo_package")content else container.packageRepository.load()
            mutable.update{it.copy(content=content,legacyPreview=preview,session=session,mode=if(BuildConfig.DEBUG)session?.sourceMode?:TourMode.MOCK else TourMode.LIVE,loading=false,error=null)}
        }catch(e:Exception){if(e is CancellationException)throw e;storageError(e);return}
        try{tours=container.tourRepository.load();mutable.update{it.copy(tourError=null)}}catch(e:Exception){
            if(e is CancellationException)throw e
            mutable.update{it.copy(tourError="관광 기록을 읽지 못했습니다. 원본을 보존하고 해금을 보류합니다.")}
        }
        if(mutable.value.mode==TourMode.LIVE&&mutable.value.foreground){runtime.setForeground(true);runtime.start()}
        refresh()
    }
    private fun validate(content:GamePackage,session:GameSession){
        if(session.packageId!=content.id||session.contentVersion!=content.contentVersion)throw GameStorageException(StorageFailure.CONTENT_MISMATCH)
        if(GameController.validateSession(content,session).isNotEmpty())throw GameStorageException(StorageFailure.INVALID_PROGRESS)
    }
    private suspend fun newGame(event:Event.NewGame){
        if(mutable.value.error?.contentError==true)return
        val mode=event.mode?:mutable.value.mode
        if(mode==TourMode.MOCK&&!BuildConfig.DEBUG){mutable.update{it.copy(sdkMessage="이 빌드에는 시뮬레이션 입력이 없습니다.")};return}
        mutable.update{it.copy(saving=true,feedback=null)}
        try{
            val content=container.packageRepository.load(event.packageId?:mutable.value.content?.id)
            val saved=container.gameRepository.reset(GameController.newSession(content,UUID.randomUUID().toString(),mode))
            runtime.stopAudio();runtime.stop();mockLocation=null;mockRevision++;container.sources.parking.invalidate()
            mutable.update{it.copy(content=content,session=saved,mode=mode,saving=false,error=null,
                rejectedGate=null,voiceDraft=null,sdkMessage=null)}
            if(mode==TourMode.LIVE&&mutable.value.foreground){runtime.setForeground(true);runtime.start()}
            refresh();destinations.send("checkpoints")
        }catch(e:Exception){if(e is CancellationException)throw e;storageError(e)}
    }
    private suspend fun process(event:Event.Command){
        refresh();val s=mutable.value;val content=s.content?:return;val session=s.session?:return
        val stale=event.command !is GameCommand.Arrive&&(event.revision!=inputRevision()||event.foregroundRevision!=foregroundRevision)
        val env=s.environment.copy(parking=if(stale)ParkingState.UNKNOWN else s.parking,foreground=event.foreground&&s.foreground)
        val transition=GameController.reduce(content,session,event.command,env)
        if(transition.session==session){mutable.update{it.copy(feedback=transition.feedback,rejectedGate=transition.gate)};return}
        mutable.update{it.copy(saving=true,feedback=null,rejectedGate=null)}
        try{
            val saved=container.gameRepository.save(transition.session)
            mutable.update{it.copy(session=saved,saving=false,feedback=transition.feedback)};refresh()
            if(GameController.currentCheckpoint(content,saved)==null)destinations.send("completion")
        }catch(e:Exception){if(e is CancellationException)throw e;storageError(e)}
    }
    private suspend fun beginTour(event:Event.StartTour){
        refresh();val s=mutable.value
        if(currentKey()!=event.key||s.session?.sourceMode!=s.mode||s.content?.requiresTour!=true||
            event.revision!=inputRevision()||s.tourStartGate!=TourGate.READY||s.tourError!=null||s.saving)return
        mutable.update{it.copy(saving=true)}
        try{
            tours=container.tourRepository.start(TourStartRecord(event.key.sessionId,event.key.checkpointId,event.key.mode,event.pressedAt))
            mutable.update{it.copy(saving=false,sdkMessage="관광 시작 시각을 기기에 저장했습니다.")};refresh()
            enqueue(GameCommand.Arrive(event.key.sessionId,event.key.checkpointId,tours.find(event.key)!!.startedAt.wallTimeMillis))
        }catch(e:Exception){if(e is CancellationException)throw e
            mutable.update{it.copy(saving=false,tourError="관광 시작을 저장하지 못했습니다. 재시도 전에는 해금하지 않습니다.")};refresh()
        }
    }
    private suspend fun debugElapsed(event:Event.DebugElapsed){
        if(!BuildConfig.DEBUG||currentKey()!=event.key||event.key.mode!=TourMode.MOCK||mutable.value.saving)return
        try{tours=container.tourRepository.markDebugTimeElapsed(event.key,true);refresh()}catch(e:Exception){
            if(e is CancellationException)throw e;mutable.update{it.copy(tourError="시연 시간 상태를 저장하지 못했습니다.")}
        }
    }
    private fun audioAllowed()=mutable.value.mode==TourMode.LIVE&&mutable.value.canReadContent&&mutable.value.let{s->
        s.content!=null&&s.session!=null&&GameController.gate(s.content,s.session,s.environment)==GateReason.READY}
    fun navigateCurrent(){
        val target=currentKey()?.let{TourCatalog.find(it.checkpointId)}?:return
        if(mutable.value.mode!=TourMode.LIVE||!mutable.value.canReadContent)return
        val accepted=runtime.navigate(target)
        mutable.update{it.copy(sdkMessage=if(accepted)"길 안내를 요청했습니다. 실제 안내 결과는 SDK 상태에서 확인합니다."
            else "길 안내 요청을 처리하지 못했습니다. SDK 권한·지원 상태를 확인하세요.")}
    }
    fun speakPrompt(){
        if(!audioAllowed())return
        val s=mutable.value;val cp=GameController.currentCheckpoint(s.content!!,s.session!!)?:return
        runtime.speak(s.content.puzzles.first{it.id==cp.puzzleId}.prompt)
    }
    fun listenAnswer(){
        if(!audioAllowed())return
        val s=mutable.value;val cp=GameController.currentCheckpoint(s.content!!,s.session!!)?:return
        val requestSession=s.session.sessionId;val requestPuzzle=cp.puzzleId;val ticket=voiceGeneration+1
        val accepted=runtime.listen{result->
            if(voiceGeneration==ticket&&audioAllowed()&&mutable.value.session?.sessionId==requestSession&&
                mutable.value.content?.let{GameController.currentCheckpoint(it,mutable.value.session!!)}?.puzzleId==requestPuzzle)
                mutable.update{it.copy(voiceDraft=result?.take(80),sdkMessage=if(result==null)"음성을 인식하지 못했습니다."else"인식 결과를 확인한 후 제출하세요.")}
        }
        if(accepted)voiceGeneration=ticket
    }
    fun submitVoiceAnswer(){
        if(!audioAllowed())return
        val s=mutable.value;val cp=GameController.currentCheckpoint(s.content!!,s.session!!)?:return
        val input=VoiceAnswerParser.parse(s.content.puzzles.first{it.id==cp.puzzleId}.lock,s.voiceDraft.orEmpty())
        if(input==null){mutable.update{it.copy(sdkMessage="이 자물쇠의 입력 형식으로 해석하지 못했습니다. 화면으로 입력하세요.")};return}
        command(GameCommand.Submit(s.session.sessionId,cp.puzzleId,input));mutable.update{it.copy(voiceDraft=null)}
    }
    fun stopAudio(){voiceGeneration++;runtime.stopAudio();mutable.update{it.copy(voiceDraft=null)}}
    fun reconnectSdk(){if(mutable.value.mode==TourMode.LIVE&&mutable.value.foreground){runtime.stop();runtime.setForeground(true);runtime.start();refresh()}}
    private fun storageError(e:Exception){mutable.update{it.copy(loading=false,saving=false,
        error=GameUiError(false,(e as?GameStorageException)?.reason?.name?:"READ_WRITE",e.message))}}
    override fun onCleared(){runtime.stop();super.onCleared()}
    companion object{fun factory(container:AppContainer)=object:ViewModelProvider.Factory{
        @Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>):T=GameViewModel(container)as T
    }}
}
