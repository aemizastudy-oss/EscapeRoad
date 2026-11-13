package com.team.carescape.ui

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.team.carescape.BuildConfig
import com.team.carescape.data.StoredTrip
import com.team.carescape.data.TripProgressStore
import com.team.carescape.game.*
import com.team.carescape.model.*
import com.team.carescape.platform.sdk.PleosSdkRuntime
import com.team.carescape.platform.sdk.PleosSdkStatus
import com.team.carescape.platform.sdk.SdkAvailability
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { LOBBY, THEME_SELECT, BRIEFING, PLAY, SOLVED, SUCCESS, STORY_BRIEFING, STORY_PROLOGUE, STORY, LOCK_WORKSHOP }

/** Existing screens use one live runtime and one serialized, durable story state. */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = PleosSdkRuntime(application)
    private val store = TripProgressStore(application)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var trip: StoredTrip? = null
    private var safetyRevision = 0L
    private var foregroundTicker: Job? = null
    private var mockLocation: TourLocation? = null
    private var mockWaypointId: String? = null
    private var mockParked: Boolean? = null
    private var puzzleDraftIdentity: String? = null
    private var liveSafety by mutableStateOf(TourSafety(false, false, false, false))
    private val bootId = runCatching {
        "boot:${Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT)}"
    }.getOrDefault("")

    var screen by mutableStateOf(Screen.LOBBY)
        private set
    var lockWorkshop by mutableStateOf(LockWorkshopState())
        private set
    var storyPuzzleDraft by mutableStateOf(StoryPuzzleDraft())
        private set
    var storyPuzzleMessage by mutableStateOf<String?>(null)
        private set
    val storyPuzzle: StoryPuzzleDefinition? get() = story?.currentPuzzle
    val storyPuzzleProgress: StoryPuzzleProgress? get() = story?.puzzleProgressForCurrent
    /** 사건 기록부(정차 중에만). 주행 상태가 되면 닫는다. */
    var casebookOpen by mutableStateOf(false)
        private set
    fun openCasebook() { if (parked && story != null && StoryCatalog.usesAwakening(story!!.flowVersion)) casebookOpen = true }
    fun closeCasebook() { casebookOpen = false }
    var selectedCard by mutableStateOf(ThemeCatalog.cards.first())
        private set
    var game by mutableStateOf<GameState?>(null)
        private set
    var vehicle by mutableStateOf(VehicleState())
        private set
    var startedAtMs by mutableStateOf(0L)
        private set
    var story by mutableStateOf<StoryState?>(null)
        private set
    var pendingNarration by mutableStateOf<String?>(null)
    var prologuePage by mutableStateOf(0)
        private set
    var showMap by mutableStateOf(false)
        private set
    var storyStartedAtMs by mutableStateOf(0L)
        private set
    var mode by mutableStateOf(if (BuildConfig.DEBUG) TourMode.MOCK else TourMode.LIVE)
        private set
    val isMock get() = BuildConfig.DEBUG && mode == TourMode.MOCK
    private var foregroundState by mutableStateOf(false)
    val foreground: Boolean get() = foregroundState
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var storageError by mutableStateOf<String?>(null)
        private set
    var sdkMessage by mutableStateOf<String?>(null)
        private set
    var sdkStatus by mutableStateOf(PleosSdkStatus())
        private set
    var tourStartGate by mutableStateOf(TourGate.BACKGROUND)
        private set
    var tourGate by mutableStateOf(TourGate.TOUR_NOT_STARTED)
        private set
    var tourRemainingMillis by mutableStateOf<Long?>(null)
        private set
    val currentWaypoint get() = TourCatalog.checkpoints.getOrNull(story?.chapterIndex ?: 0)
    val parked: Boolean get() = foreground && !loading && storageError == null && vehicle.parked == true &&
        if (isMock) mockParked == true else runtime.safety.value.let {
            it.foreground && it.parked && it.stationary && it.carUxAllowed
        }
    val speechReady get() = parked && liveSafety.let { it.foreground && it.parked && it.stationary && it.carUxAllowed } &&
        sdkStatus.tts.availability == SdkAvailability.READY

    init {
        viewModelScope.launch {
            for (command in commands) {
                try { command() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { storageError = error.message ?: "여행 기록 처리에 실패했습니다. 다시 시도해 주세요." }
                finally { saving = false }
            }
        }
        commands.trySend { loadCurrentMode() }
        viewModelScope.launch { runtime.safety.collect { liveSafety = it; updateLiveInputs() } }
        viewModelScope.launch { runtime.locations.collect { refreshTour() } }
        viewModelScope.launch { runtime.status.collect { sdkStatus = it; updateLiveInputs() } }
    }

    private fun now() = TourClock(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootId)
    private fun safety() = if (isMock) TourSafety(foreground, mockParked == true, mockParked == true, mockParked == true)
        else runtime.safety.value.copy(foreground = foreground && runtime.safety.value.foreground)
    private fun location() = if (isMock) mockLocation.takeIf { mockWaypointId == currentWaypoint?.id } else runtime.locations.value
    private fun record() = trip?.tourRecords?.firstOrNull { it.checkpointId == currentWaypoint?.id }

    private suspend fun loadCurrentMode() {
        loading = true
        try {
            val saved = store.load()
            trip = saved.trips.firstOrNull { it.mode == mode }
            applyStored(trip)
            storageError = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { storageError = error.message ?: "여행 기록을 읽지 못했습니다. 원본을 보존합니다." }
        finally { loading = false; updateLiveInputs() }
    }

    private fun applyStored(value: StoredTrip?) {
        story = value?.let {
            StoryState(StoryCatalog.episodeFor(it.flowVersion), it.chapterIndex, it.phase, it.items, it.clues,
                flowVersion = it.flowVersion, legacyCompletedCount = it.legacyCompletedCount,
                puzzleProgress = it.puzzleProgress)
        }
        val step = story?.takeIf { it.phase == StoryPhase.PUZZLE }?.currentStepIndex
        val identity = value?.let { "${it.sessionId}:${it.chapterIndex}:${it.phase}:$step" }
        if (identity != puzzleDraftIdentity) {
            puzzleDraftIdentity = identity
            storyPuzzleDraft = StoryPuzzleDraft.forLock(story?.takeIf { it.phase == StoryPhase.PUZZLE }?.currentPuzzle?.lock)
            storyPuzzleMessage = null
        }
        storyStartedAtMs = value?.startedAtEpochMillis ?: 0L
        showMap = value?.showMap ?: false
    }

    private fun refreshTour() {
        val target = currentWaypoint ?: return
        val clock = now()
        tourStartGate = TourPolicy.startGate(mode, target, location(), safety(), clock)
        tourGate = TourPolicy.unlockGate(record(), mode, target, location(), safety(), clock)
        tourRemainingMillis = record()?.let {
            when (val elapsed = TourPolicy.elapsed(it, clock)) {
                is TourTimeResult.Valid -> if (isMock && it.debugTimeBypass) 0L else
                    (TourPolicy.MIN_TOUR_MILLIS - elapsed.elapsedMillis).coerceAtLeast(0L)
                is TourTimeResult.Pending -> null
            }
        }
        if (screen == Screen.STORY && story?.phase == StoryPhase.EN_ROUTE && !showMap &&
            tourStartGate == TourGate.READY && !saving && storageError == null
        ) changeTrip { old ->
            if (TourPolicy.startGate(mode, target, location(), safety(), now()) == TourGate.READY)
                old?.copy(phase = StoryPhase.ARRIVED) else null
        }
    }

    private fun updateLiveInputs() {
        val previouslySafe = vehicle.parked == true
        val safe = if (isMock) mockParked else runtime.safety.value.let {
            when {
                it.foreground && it.parked && it.stationary && it.carUxAllowed -> true
                sdkStatus.carSafety.availability == SdkAvailability.READY && (!it.parked || !it.stationary) -> false
                else -> null
            }
        }
        vehicle = if (isMock) vehicle.copy(parked = mockParked) else VehicleState(
            parked = safe, batteryPercent = sdkStatus.batteryPercent,
        )
        // Receiving the first live snapshot is a baseline, never a turn/lighting action.
        game = game?.copy(lastVehicle = vehicle)
        if (previouslySafe && safe != true) { safetyRevision++; stopAudio(); casebookOpen = false }
        refreshTour()
    }

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        safetyRevision++
        foregroundState = value
        foregroundTicker?.cancel()
        foregroundTicker = null
        if (!value) {
            mockParked = null; mockLocation = null; mockWaypointId = null
            vehicle = VehicleState(); pendingNarration = null
            storyPuzzleDraft = StoryPuzzleDraft.forLock(storyPuzzle?.lock); storyPuzzleMessage = null
            casebookOpen = false
            runtime.stopAudio()
        }
        runtime.setForeground(value)
        if (value) {
            runtime.start()
            foregroundTicker = viewModelScope.launch { while (foreground) { refreshTour(); delay(1_000) } }
        } else runtime.stop()
        refreshTour()
    }

    fun chooseMode(next: TourMode) {
        if (!BuildConfig.DEBUG || next == mode || saving || screen != Screen.LOBBY) return
        safetyRevision++
        runtime.stopAudio()
        pendingNarration = null; mockLocation = null; mockWaypointId = null; mockParked = null
        storyPuzzleDraft = StoryPuzzleDraft(); storyPuzzleMessage = null; puzzleDraftIdentity = null
        vehicle = VehicleState(); story = null; trip = null; game = null
        mode = next; loading = true
        commands.trySend { loadCurrentMode() }
    }

    fun retryStorage() { if (!saving && !loading) commands.trySend { loadCurrentMode() } }
    fun reconnectSdk() {
        if (!foreground) return
        safetyRevision++
        runtime.stop(); runtime.setForeground(true); runtime.start()
        refreshTour()
    }
    fun onPermissionsChanged() = reconnectSdk()
    fun speakNarration(text: String): Boolean = parked && runtime.speak(text)
    fun stopAudio() { runtime.stopAudio() }

    /** Serialize a click against its original mode, session, phase and safety generation. */
    private fun changeTrip(
        afterSave: (() -> Unit)? = null,
        transform: (StoredTrip?) -> StoredTrip?,
    ) {
        if (!parked || saving || loading || storageError != null) return
        val previous = trip
        val requestedMode = mode
        val revision = safetyRevision
        val liveRevision = runtime.revision
        val sourceScreen = screen
        commands.trySend {
            if (mode != requestedMode || trip != previous || revision != safetyRevision || screen != sourceScreen || !parked ||
                requestedMode == TourMode.LIVE && liveRevision != runtime.revision) return@trySend
            saving = true
            val next = transform(previous) ?: return@trySend
            if (next == previous) { afterSave?.invoke(); return@trySend }
            val saved = store.save(next)
            if (mode == requestedMode) {
                trip = saved; applyStored(saved)
                // Publish the new start clock and gate before exposing the completed save.
                refreshTour()
                if (revision == safetyRevision && screen == sourceScreen && parked) afterSave?.invoke()
            }
        }
    }

    fun goLobby() { safetyRevision++; stopAudio(); pendingNarration = null; screen = Screen.LOBBY }
    fun openLockWorkshop() {
        if (!parked || saving) return
        safetyRevision++; stopAudio(); pendingNarration = null
        lockWorkshop = LockWorkshopState()
        screen = Screen.LOCK_WORKSHOP
    }
    private fun canEditWorkshop(kind: WorkshopLock = lockWorkshop.selected): Boolean =
        parked && !saving && screen == Screen.LOCK_WORKSHOP && kind == lockWorkshop.selected

    fun selectWorkshopLock(kind: WorkshopLock) {
        if (canEditWorkshop()) lockWorkshop = lockWorkshop.copy(selected = kind, submitted = null)
    }
    fun changeWorkshopColors(value: List<DialColor>) {
        if (canEditWorkshop(WorkshopLock.COLOR) && value.size == 4)
            lockWorkshop = lockWorkshop.copy(colors = value.toList(), submitted = null)
    }
    fun changeWorkshopDirections(value: List<LockDirection>) {
        if (canEditWorkshop(WorkshopLock.DIRECTION))
            lockWorkshop = lockWorkshop.copy(directions = value.toList(), submitted = null)
    }
    fun changeWorkshopEnglish(value: String) {
        if (canEditWorkshop(WorkshopLock.ENGLISH))
            lockWorkshop = lockWorkshop.copy(english = LockInput.normalizeEnglish(value), submitted = null)
    }
    fun changeWorkshopNumbers(value: List<Int>) {
        if (canEditWorkshop(WorkshopLock.NUMBER) && value.size == 4 && value.all { it in 0..9 })
            lockWorkshop = lockWorkshop.copy(numbers = value.toList(), submitted = null)
    }
    fun submitWorkshopColors(value: List<DialColor>) {
        if (canEditWorkshop(WorkshopLock.COLOR) && value == lockWorkshop.colors)
            lockWorkshop = lockWorkshop.copy(submitted = value.joinToString(" · ") { it.label })
    }
    fun submitWorkshopDirections(value: List<LockDirection>) {
        if (canEditWorkshop(WorkshopLock.DIRECTION) && value.isNotEmpty() && value == lockWorkshop.directions)
            lockWorkshop = lockWorkshop.copy(submitted = value.joinToString(" ") { it.symbol })
    }
    fun submitWorkshopEnglish(value: String) {
        if (canEditWorkshop(WorkshopLock.ENGLISH) && value.length == 5 && value == lockWorkshop.english)
            lockWorkshop = lockWorkshop.copy(submitted = value)
    }
    fun submitWorkshopNumbers(value: List<Int>) {
        if (canEditWorkshop(WorkshopLock.NUMBER) && value == lockWorkshop.numbers)
            lockWorkshop = lockWorkshop.copy(submitted = value.joinToString(""))
    }
    fun goThemeSelect() { if (parked) screen = Screen.THEME_SELECT }
    fun select(card: ThemeCard) { if (parked && card.playable) selectedCard = card }
    fun goBriefing() {
        if (!parked || !selectedCard.playable) return
        screen = if (selectedCard.story != null) Screen.STORY_BRIEFING else Screen.BRIEFING
    }
    fun goStoryBriefing() { if (parked && selectedCard.story != null) screen = Screen.STORY_BRIEFING }
    fun goPrologue() {
        if (!parked || saving) return
        val episode = selectedCard.story ?: return
        if (story != null && story?.phase != StoryPhase.ENDED) { resumeStory(); return }
        prologuePage = 0; pendingNarration = prologuePagesOf(episode).first(); screen = Screen.STORY_PROLOGUE
    }
    fun prologueNext() {
        if (!parked) return
        val episode = selectedCard.story ?: return
        val pages = prologuePagesOf(episode)
        if (prologuePage < pages.lastIndex) { prologuePage++; pendingNarration = pages[prologuePage] }
        else startStory()
    }
    fun startStory() {
        if (selectedCard.story?.id != Siyeok.episode.id) return
        changeTrip(afterSave = { screen = Screen.STORY }) {
            if (it != null && it.phase != StoryPhase.ENDED) it else StoredTrip(
                mode = mode, sessionId = UUID.randomUUID().toString(), chapterIndex = 0,
                phase = StoryPhase.EN_ROUTE, startedAtEpochMillis = System.currentTimeMillis(), showMap = true,
                flowVersion = StoryCatalog.CURRENT_FLOW_VERSION,
            )
        }
    }
    fun resumeStory() { if (parked && story != null) { screen = Screen.STORY; refreshTour() } }

    fun startGuidance() {
        if (!parked || story == null) return
        val target = currentWaypoint ?: return
        if (!isMock && sdkStatus.navigation.availability != SdkAvailability.READY) {
            sdkMessage = "길 안내 연결을 확인한 뒤 다시 시도해 주세요."; return
        }
        changeTrip(afterSave = {
            if (isMock) sdkMessage = "시연 경로입니다. 실제 길 안내를 요청하지 않았습니다."
            else sdkMessage = if (runtime.navigate(target)) "길 안내를 요청했습니다. 결과는 내비게이션에서 확인해 주세요."
                else "길 안내를 요청하지 못했습니다. SDK 연결과 안전 상태를 확인해 주세요."
        }) { it?.copy(showMap = false) }
    }

    fun demoArrive() {
        if (!isMock || !parked || story == null) return
        val clock = now()
        mockWaypointId = currentWaypoint?.id
        mockLocation = TourLocation(TourMode.MOCK, accuracyMeters = 1.0,
            timestampEpochMillis = clock.wallTimeMillis, receivedElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            simulatedInsideRadius = true)
        refreshTour()
    }
    fun leaveCar() {
        refreshTour()
        if (tourStartGate != TourGate.READY || story?.phase != StoryPhase.ARRIVED) return
        changeTrip { old ->
            if (old == null || TourPolicy.startGate(mode, currentWaypoint!!, location(), safety(), now()) != TourGate.READY) null
            else {
                val existing = old.tourRecords.firstOrNull { it.checkpointId == currentWaypoint!!.id }
                val started = existing ?: TourStartRecord(old.sessionId, currentWaypoint!!.id, mode, now())
                old.copy(phase = StoryPhase.EXPLORING,
                    tourRecords = if (existing == null) old.tourRecords + started else old.tourRecords)
            }
        }
    }
    fun simElapseTour() {
        if (!isMock || story?.phase != StoryPhase.EXPLORING) return
        changeTrip { old -> old?.copy(tourRecords = old.tourRecords.map {
            if (it.checkpointId == currentWaypoint?.id) it.copy(debugTimeBypass = true) else it
        }) }
    }
    fun returnToCar() {
        refreshTour()
        if (tourGate != TourGate.READY || story?.phase != StoryPhase.EXPLORING) return
        changeTrip { old ->
            if (old == null || TourPolicy.unlockGate(record(), mode, currentWaypoint!!, location(), safety(), now()) != TourGate.READY) null
            else StoryEngine.returnToCar(story!!).let {
                old.copy(phase = it.phase, items = it.items, clues = it.clues, puzzleProgress = it.puzzleProgress)
            }
        }
    }

    /** Draft changes are local and never grant a clue or write a puzzle result. */
    private fun canEditStoryPuzzle(): Boolean = parked && !saving && !loading &&
        storageError == null && screen == Screen.STORY && story?.phase == StoryPhase.PUZZLE &&
        tourGate == TourGate.READY && storyPuzzleProgress?.solved == false

    fun changeStoryNumbers(value: List<Int>) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.Numeric ?: return
        if (!canEditStoryPuzzle() || value.size != lock.answer.length || value.any { it !in 0..9 }) return
        storyPuzzleDraft = storyPuzzleDraft.copy(numbers = value.toList())
        storyPuzzleMessage = null
    }
    fun changeStoryDirections(value: List<LockDirection>) {
        if (!canEditStoryPuzzle() || storyPuzzle?.lock !is StoryLockDefinition.Directions) return
        storyPuzzleDraft = storyPuzzleDraft.copy(directions = value.toList())
        storyPuzzleMessage = null
    }
    fun changeStoryDevice(channel: Int, level: Int) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.Device ?: return
        if (!canEditStoryPuzzle() || channel !in lock.min..lock.max || level !in lock.min..lock.max) return
        storyPuzzleDraft = storyPuzzleDraft.copy(channel = channel, level = level)
        storyPuzzleMessage = null
    }
    fun changeStoryChoice(id: String) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.Choice ?: return
        if (!canEditStoryPuzzle() || lock.options.none { it.id == id }) return
        storyPuzzleDraft = storyPuzzleDraft.copy(choice = id); storyPuzzleMessage = null
    }
    fun changeStoryOrder(ids: List<String>) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.Order ?: return
        if (!canEditStoryPuzzle() || ids.distinct().size != ids.size || ids.any { id -> lock.items.none { it.id == id } }) return
        storyPuzzleDraft = storyPuzzleDraft.copy(order = ids.toList()); storyPuzzleMessage = null
    }
    fun changeStoryCar(value: List<CarControl>) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.CarSequence ?: return
        if (!canEditStoryPuzzle() || value.size > lock.maxLength || value.any { it !in lock.controls }) return
        storyPuzzleDraft = storyPuzzleDraft.copy(car = value.toList()); storyPuzzleMessage = null
    }
    private fun draftInput(): StoryPuzzleInput? = when (storyPuzzle?.lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(storyPuzzleDraft.numbers.joinToString(""))
        is StoryLockDefinition.Directions -> StoryPuzzleInput.Directions(storyPuzzleDraft.directions.toList())
        is StoryLockDefinition.Device -> StoryPuzzleInput.Device(storyPuzzleDraft.channel, storyPuzzleDraft.level)
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(storyPuzzleDraft.choice ?: "")
        is StoryLockDefinition.Order -> StoryPuzzleInput.Order(storyPuzzleDraft.order.toList())
        is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(storyPuzzleDraft.car.toList())
        null -> null
    }

    /** Submits a snapshot only; refresh location/safety again at the serialized mutation boundary. */
    fun submitStoryPuzzle(input: StoryPuzzleInput) {
        refreshTour()
        if (!canEditStoryPuzzle() || input != draftInput()) return
        val puzzleId = storyPuzzle?.id ?: return
        val verdict = storyPuzzle?.verdict == true
        var outcome = StoryPuzzleResult.LOCKED
        var note: String? = null
        val message = {
            when (outcome) {
                StoryPuzzleResult.CORRECT -> note ?: if (verdict) "추리를 기록했습니다." else "자물쇠가 열렸습니다. 단서를 저장했습니다."
                StoryPuzzleResult.WRONG -> note ?: "아직 열리지 않았습니다. 단서를 다시 살펴보세요."
                StoryPuzzleResult.INVALID_INPUT -> "입력 개수와 값을 확인한 뒤 제출해 주세요."
                StoryPuzzleResult.LOCKED -> "현재 문제와 이용 조건을 다시 확인해 주세요."
            }
        }
        // 저장 후 applyStored가 초안을 비운 다음에 결과 대사를 보여 준다.
        changeTrip(afterSave = { storyPuzzleMessage = message() }) { old ->
            if (old == null || story?.currentPuzzle?.id != puzzleId || story?.phase != StoryPhase.PUZZLE ||
                TourPolicy.unlockGate(record(), mode, currentWaypoint!!, location(), safety(), now()) != TourGate.READY) null
            else {
                val result = StoryEngine.submitPuzzle(story!!, input)
                outcome = result.result
                note = result.note
                old.copy(phase = result.state.phase, items = result.state.items, clues = result.state.clues,
                    puzzleProgress = result.state.puzzleProgress)
            }
        }
    }
    fun showStoryPuzzleHint() {
        refreshTour()
        if (!canEditStoryPuzzle()) return
        val puzzleId = storyPuzzle?.id ?: return
        changeTrip { old ->
            if (old == null || story?.currentPuzzle?.id != puzzleId ||
                TourPolicy.unlockGate(record(), mode, currentWaypoint!!, location(), safety(), now()) != TourGate.READY) null
            else old.copy(puzzleProgress = StoryEngine.showPuzzleHint(story!!).puzzleProgress)
        }
    }
    fun storyNext() {
        if (story?.phase !in listOf(StoryPhase.REVEALED, StoryPhase.FINAL_REVEAL)) return
        changeTrip { old -> old?.let {
            val next = StoryEngine.next(story!!)
            it.copy(chapterIndex = next.chapterIndex, phase = next.phase, items = next.items, clues = next.clues,
                showMap = next.phase == StoryPhase.EN_ROUTE, puzzleProgress = next.puzzleProgress)
        } }
    }
    fun replayDrama() { if (parked) story?.let { pendingNarration = it.chapter.travelDrama } }
    // Leaving the screen preserves the durable trip. A new completed trip starts a new session.
    fun endStory() { goLobby() }
    fun restartStory() { if (parked) { pendingNarration = null; screen = Screen.STORY_BRIEFING } }

    // Retained puzzle demo. It never accepts unsafe or release mock inputs.
    fun startCase() {
        if (!parked) return
        val episode = selectedCard.episode ?: return
        game = PuzzleEngine.onVehicle(PuzzleEngine.start(episode), vehicle)
        startedAtMs = System.currentTimeMillis(); screen = Screen.PLAY
    }
    fun resume() { if (parked && game != null) screen = if (game!!.finished) Screen.SUCCESS else Screen.PLAY }
    fun submitCode(input: String) { advance { PuzzleEngine.submitCode(it, input) } }
    fun submitChoice(index: Int) { advance { PuzzleEngine.submitChoice(it, index) } }
    fun showHint() { advance { PuzzleEngine.showHint(it) } }
    fun continueAfterSolved() { if (parked) screen = if (game?.finished == true) Screen.SUCCESS else Screen.PLAY }
    fun abandon() { if (parked) { game = null; goLobby() } }
    fun simSetParked(value: Boolean) {
        if (!isMock || !foreground) return
        mockParked = value; updateLiveInputs()
    }
    fun simToggleLowBeam() { if (isMock) updateMockVehicle(vehicle.copy(lowBeamOn = vehicle.lowBeamOn != true)) }
    fun simToggleWiper() { if (isMock) updateMockVehicle(vehicle.copy(wiperOn = vehicle.wiperOn != true)) }
    fun simSetBattery(percent: Int?) { if (isMock) updateMockVehicle(vehicle.copy(batteryPercent = percent)) }
    fun simTurnSignal(side: TurnSide) {
        if (!isMock) return
        updateMockVehicle(vehicle.copy(turnSignal = side)); updateMockVehicle(vehicle.copy(turnSignal = null))
    }
    private fun updateMockVehicle(next: VehicleState) {
        if (!isMock || !parked) return
        vehicle = next
        advance { PuzzleEngine.onVehicle(it, next) }
    }
    private fun advance(step: (GameState) -> GameState) {
        if (!parked || screen != Screen.PLAY) return
        val before = game ?: return
        val after = step(before); game = after
        if (after.feedback == Feedback.CORRECT && after.puzzleIndex > before.puzzleIndex) screen = Screen.SOLVED
    }
    override fun onCleared() { runtime.close(); commands.close(); super.onCleared() }
}

/** 프롤로그를 시안처럼 세 쪽으로 나눈다. */
fun prologuePagesOf(ep: StoryEpisode): List<String> =
    if (ep.rules.isNotEmpty()) listOf(ep.prologue) + ep.rules.map { "이야기의 규칙\n$it" }
    else if (ep.id != "siyeok") listOf(ep.prologue)
    else listOf(
        "\"요원, 브리핑을 시작한다. 1795년 윤2월, 화성에 정체불명의 역병이 돌았다.\n죽은 자가 다시 일어나는 병이다.\"",
        "\"당시 내의원이 치료제의 비방을 만들었지만, 기록은 흩어졌고 약은 끝내 쓰이지 못했다.\n그 결과 역사가 무너지고 있다.\"",
        "\"1795년으로 건너가 비방과 재료를 모으고, 백신을 완성해 전달하라.\n첫 번째 실마리는 왕의 어머니를 위한 잔치가 열린 곳에 있다.\"",
    )
