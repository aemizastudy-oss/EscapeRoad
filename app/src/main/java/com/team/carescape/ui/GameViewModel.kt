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

enum class Screen { LOBBY, THEME_SELECT, BRIEFING, PLAY, SOLVED, SUCCESS, STORY_BRIEFING, STORY_PROLOGUE, STORY, LOCK_WORKSHOP, TRAVEL_GUIDE }

/** Existing screens use one live runtime and one serialized, durable story state. */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = PleosSdkRuntime(application)
    private val store = TripProgressStore(application)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var trip: StoredTrip? = null
    private var safetyRevision = 0L
    private var foregroundTicker: Job? = null
    private val tourVisit = TourVisitConfirmation()
    private var mockLocation: TourLocation? = null
    private var mockWaypointId: String? = null
    private var mockParked: Boolean? = null
    private var puzzleDraftIdentity: String? = null
    private val vehiclePuzzleMapper = VehiclePuzzleInputMapper()
    private var vehiclePuzzleContextKey: String? = null
    private var vehiclePuzzleResetRevision = 0L
    private var mockVehicleSdkState by mutableStateOf(false)
    /** Explicit debug hybrid: MOCK tourism, actual SDK safety and puzzle inputs. Never persisted. */
    val mockVehiclePuzzleSdk: Boolean get() = BuildConfig.DEBUG && isMock && mockVehicleSdkState
    private val usesVehiclePuzzleSdk: Boolean get() = !isMock || mockVehiclePuzzleSdk
    var vehiclePuzzleValues by mutableStateOf<Map<CarControl, Boolean?>>(emptyMap())
        private set
    var vehiclePuzzleSignals by mutableStateOf<Map<CarControl, com.team.carescape.platform.sdk.SdkFeatureState>>(emptyMap())
        private set
    var carPuzzleInput by mutableStateOf<CarPuzzleInput>(CarPuzzleInput.Manual)
        private set
    private var liveSafety by mutableStateOf(TourSafety(false, false, false, false))
    private val bootId = runCatching {
        "boot:${Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT)}"
    }.getOrDefault("")

    private var screenState by mutableStateOf(Screen.LOBBY)
    var screen: Screen
        get() = screenState
        private set(value) {
            if (screenState != value) {
                screenState = value
                synchronizeVehiclePuzzle()
            }
        }
    var lockWorkshop by mutableStateOf(LockWorkshopState())
        private set
    var storyPuzzleDraft by mutableStateOf(StoryPuzzleDraft())
        private set
    var storyPuzzleMessage by mutableStateOf<String?>(null)
        private set
    val storyPuzzle: StoryPuzzleDefinition? get() = story?.currentPuzzle
    /** 탐색 장면에서는 열어 둔 자물쇠 기준으로 힌트 수·풀림을 보여 준다. */
    val storyPuzzleProgress: StoryPuzzleProgress? get() {
        val s = story ?: return null
        val raw = s.puzzleProgressForCurrent ?: return null
        if (!s.isExplore) return raw
        val focus = s.exploreFocus ?: return raw
        return raw.copy(hintsShown = raw.stepHints[focus] ?: 0, solved = raw.solved || focus in raw.solvedSteps)
    }
    /** 탐색 장면에서 열어 둔 자물쇠(저장하지 않는 화면 상태). */
    var exploreFocus by mutableStateOf<String?>(null)
        private set
    /** 장면 힌트로 반짝이는 조사할 곳. */
    var sceneGlow by mutableStateOf<String?>(null)
        private set
    /** 장면에서 마지막으로 살펴본 곳. */
    var exploreSelected by mutableStateOf<String?>(null)
        private set
    /** 방금 처음 찾은 곳(첫 살펴보기 글을 보여 준다). */
    var exploreFresh by mutableStateOf<String?>(null)
        private set
    /** 사건 기록부(정차 중에만). 주행 상태가 되면 닫는다. */
    var casebookOpen by mutableStateOf(false)
        private set
    fun openCasebook() {
        if (parked && story != null && StoryCatalog.usesAwakening(story!!.flowVersion)) {
            safetyRevision++
            casebookOpen = true
            synchronizeVehiclePuzzle()
        }
    }
    fun closeCasebook() { casebookOpen = false; synchronizeVehiclePuzzle() }
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
    /** 엔딩에 닿은 시각(없으면 null). */
    var storyEndedAtMs by mutableStateOf<Long?>(null)
        private set
    /** 경유지별 관광 시작 시각(보고서용). */
    var storyTourStarts by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    /** 엔딩 화면 위에 연 패널("retry"·"report"). 사건 기록부를 다녀와도 남는다. */
    var endingPanel by mutableStateOf("")
    /** 진상 다시 풀기에서 고른 답(단계 id → 보기 id). */
    var truthDraft by mutableStateOf(mapOf<String, String>())
    /** 진상 다시 풀기 결과 안내. */
    var truthRetryMessage by mutableStateOf<String?>(null)
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
        if (isMock && !mockVehiclePuzzleSdk) mockParked == true else runtime.safety.value.let {
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
                finally { saving = false; synchronizeVehiclePuzzle() }
            }
        }
        commands.trySend { loadCurrentMode() }
        viewModelScope.launch { runtime.safety.collect { liveSafety = it; updateLiveInputs() } }
        viewModelScope.launch { runtime.locations.collect { refreshTour() } }
        viewModelScope.launch { runtime.status.collect { sdkStatus = it; updateLiveInputs() } }
        viewModelScope.launch { runtime.puzzleStatus.collect { vehiclePuzzleSignals = it; vehiclePuzzleValues = runtime.puzzleLatest.mapValues { sample -> sample.value.active }; synchronizeVehiclePuzzle() } }
        viewModelScope.launch { runtime.puzzleSamples.collect { sample ->
            vehiclePuzzleValues = runtime.puzzleLatest.mapValues { it.value.active }
            refreshTour()
            val lock = storyPuzzle?.lock as? StoryLockDefinition.CarSequence
            if (usesVehiclePuzzleSdk && lock != null && carPuzzleInput is CarPuzzleInput.Vehicle &&
                (carPuzzleInput as CarPuzzleInput.Vehicle).ready && canEditStoryPuzzle()) {
                val control = vehiclePuzzleMapper.accept(sample, SystemClock.elapsedRealtime())
                if (control != null && control in lock.controls && storyPuzzleDraft.car.size < lock.maxLength) {
                    storyPuzzleDraft = storyPuzzleDraft.copy(car = storyPuzzleDraft.car + control)
                    storyPuzzleMessage = null
                }
            }
        } }
    }

    private fun now() = TourClock(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootId)
    private fun safety() = if (isMock && !mockVehiclePuzzleSdk) TourSafety(foreground, mockParked == true, mockParked == true, mockParked == true)
        else runtime.safety.value.copy(foreground = foreground && runtime.safety.value.foreground)
    private fun rawLocation() = if (isMock) mockLocation.takeIf { mockWaypointId == currentWaypoint?.id } else runtime.locations.value
    private fun location() = tourVisit.locationForGate(rawLocation())
    private fun unlockTourGate(clock: TourClock = now()): TourGate {
        val target = currentWaypoint ?: return TourGate.COORDINATES_NOT_CONFIGURED
        val departureAt = if (isMock && !mockVehiclePuzzleSdk) clock.elapsedRealtimeMillis.takeIf { mockParked == false }
            else runtime.lastDepartureElapsedMillis
        tourVisit.observe(record(), mode, target, rawLocation(), safety(), clock, departureAt)
        return TourPolicy.unlockGate(record(), mode, target, location(), safety(), clock, tourVisit.confirmation)
    }
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
                puzzleProgress = it.puzzleProgress, exploreFocus = exploreFocus)
        }
        if (story?.phase != StoryPhase.PUZZLE || story?.isExplore != true) {
            exploreFocus = null; sceneGlow = null; exploreSelected = null
            story = story?.copy(exploreFocus = null)
        } else if (story?.currentPuzzle == null && exploreFocus != null) {
            // 방금 푼 자물쇠는 닫고 장면으로 돌아간다.
            exploreFocus = null
            story = story?.copy(exploreFocus = null)
        }
        val step = story?.takeIf { it.phase == StoryPhase.PUZZLE }?.currentStepIndex
        val identity = value?.let { "${it.sessionId}:${it.chapterIndex}:${it.phase}:$step:$exploreFocus" }
        if (identity != puzzleDraftIdentity) {
            puzzleDraftIdentity = identity
            storyPuzzleDraft = StoryPuzzleDraft.forLock(story?.takeIf { it.phase == StoryPhase.PUZZLE }?.currentPuzzle?.lock)
            storyPuzzleMessage = null
        }
        storyStartedAtMs = value?.startedAtEpochMillis ?: 0L
        storyEndedAtMs = value?.endedAtEpochMillis
        storyTourStarts = value?.tourRecords?.associate { it.checkpointId to it.startedAt.wallTimeMillis }.orEmpty()
        showMap = value?.showMap ?: false
    }

    private fun refreshTour() {
        val target = currentWaypoint ?: run { synchronizeVehiclePuzzle(); return }
        val clock = now()
        tourGate = unlockTourGate(clock)
        tourStartGate = TourPolicy.startGate(mode, target, location(), safety(), clock)
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
        synchronizeVehiclePuzzle()
    }

    /** Boundaries capture a queue watermark; cached snapshots can only establish a baseline. */
    private fun synchronizeVehiclePuzzle(forceReset: Boolean = false) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.CarSequence
        val now = SystemClock.elapsedRealtime()
        val required = lock?.controls.orEmpty()
        val signalsReady = required.isNotEmpty() && required.all { control ->
            vehiclePuzzleSignals[control]?.availability == SdkAvailability.READY &&
                runtime.puzzleLatest[control]?.let { it.active != null && now - it.receivedElapsedMillis in 0..9_000L } == true
        }
        val eligible = usesVehiclePuzzleSdk && lock != null && signalsReady && canEditStoryPuzzle()
        val scope = if (usesVehiclePuzzleSdk && lock != null && screen == Screen.STORY)
            "${trip?.sessionId}:${story?.chapterIndex}:${storyPuzzle?.id}:$exploreFocus:$vehiclePuzzleResetRevision:${runtime.lastDepartureElapsedMillis}"
            else null
        val key = "$scope:$eligible:${runtime.puzzleGeneration}"
        if (forceReset || key != vehiclePuzzleContextKey) {
            vehiclePuzzleContextKey = key
            if (usesVehiclePuzzleSdk && storyPuzzleDraft.car.isNotEmpty()) storyPuzzleDraft = storyPuzzleDraft.copy(car = emptyList())
            vehiclePuzzleMapper.reset(VehiclePuzzleInputContext(scope, eligible, now, runtime.puzzleGeneration, runtime.puzzleSequence))
            if (eligible) runtime.puzzleLatest.values.forEach(vehiclePuzzleMapper::seedBaseline)
        }
        carPuzzleInput = if (!usesVehiclePuzzleSdk) CarPuzzleInput.Manual else {
            val unavailable = required.filter { vehiclePuzzleSignals[it]?.availability != SdkAvailability.READY }
            val message = when {
                unavailable.isNotEmpty() -> unavailable.joinToString(" · ") { control ->
                    val state = vehiclePuzzleSignals[control]
                    "${control.label}: ${state?.detail ?: "차량 표본 대기"}"
                }
                !eligible -> "정차·관광 조건과 최신 차량 표본을 확인하고 있습니다."
                else -> {
                    val guide = when {
                        lock?.visual is CarVisual.Sluice && CarControl.TURN_RIGHT in required ->
                            "오른쪽 깜빡이=수문 닫기 · 왼쪽=수문 열기. 같은 방향을 반복할 때는 껐다 켜세요. 켜 둔 동안의 점멸은 한 번만 입력됩니다."
                        CarControl.TURN_LEFT in required || CarControl.TURN_RIGHT in required ->
                            "깜빡이를 켜면 해당 방향을 1회 입력합니다. 같은 방향을 반복할 때는 껐다 켜세요."
                        else -> "와이퍼는 꺼짐→켜짐, 운전석 창문은 완전히 닫힘→열림을 1회로 읽습니다."
                    }
                    "${if (mockVehiclePuzzleSdk) "시연 관광 + " else ""}실제 차량 입력 · $guide 제출 버튼으로 답을 확인합니다."
                }
            }
            CarPuzzleInput.Vehicle(eligible, message)
        }
    }

    private fun updateLiveInputs() {
        val previouslySafe = vehicle.parked == true
        val safe = if (isMock && !mockVehiclePuzzleSdk) mockParked else runtime.safety.value.let {
            when {
                it.foreground && it.parked && it.stationary && it.carUxAllowed -> true
                sdkStatus.carSafety.availability == SdkAvailability.READY && (!it.parked || !it.stationary) -> false
                else -> null
            }
        }
        vehicle = if (isMock) vehicle.copy(parked = safe) else VehicleState(
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
            tourVisit.clear()
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

    fun setMockVehiclePuzzleSdk(enabled: Boolean) {
        if (!BuildConfig.DEBUG || !isMock || !foreground || loading || saving || screen != Screen.LOBBY || enabled == mockVehicleSdkState) return
        safetyRevision++
        mockVehicleSdkState = enabled
        mockParked = null
        vehicle = VehicleState()
        storyPuzzleDraft = storyPuzzleDraft.copy(car = emptyList())
        vehiclePuzzleResetRevision++
        tourVisit.clear()
        sdkMessage = if (enabled) "시연 관광 + 실제 SDK 차량 입력. P·정지·CarUx는 실제 신호로 확인합니다." else "MOCK 관광·차량 입력으로 돌아왔습니다. P 정차를 다시 입력하세요."
        updateLiveInputs()
        synchronizeVehiclePuzzle(forceReset = true)
    }

    fun chooseMode(next: TourMode) {
        if (!BuildConfig.DEBUG || next == mode || saving || screen != Screen.LOBBY) return
        safetyRevision++
        runtime.stopAudio()
        pendingNarration = null; mockLocation = null; mockWaypointId = null; mockParked = null
        storyPuzzleDraft = StoryPuzzleDraft(); storyPuzzleMessage = null; puzzleDraftIdentity = null
        vehicle = VehicleState(); story = null; trip = null; game = null
        tourVisit.clear()
        mockVehicleSdkState = false
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
            synchronizeVehiclePuzzle()
            val next = transform(previous)?.let { t ->
                // 엔딩에 처음 닿은 시각을 남긴다(사건 종결 보고서용).
                if (t.phase == StoryPhase.ENDED && t.endedAtEpochMillis == null) t.copy(endedAtEpochMillis = System.currentTimeMillis()) else t
            } ?: return@trySend
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

    fun goLobby() { safetyRevision++; stopAudio(); pendingNarration = null; endingPanel = ""; truthDraft = emptyMap(); screen = Screen.LOBBY; synchronizeVehiclePuzzle() }
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
    /** 여행 도우미(부가 서비스). 방탈출 진행 상태는 바꾸지 않는다. */
    fun openTravelGuide() { if (parked) { sdkMessage = null; screen = Screen.TRAVEL_GUIDE } }
    /** 여행 도우미의 장소로 길 안내. 정차 중에만, 시연(MOCK)에서는 실제 안내를 요청하지 않는다. */
    fun guideToPlace(name: String, lat: Double, lng: Double) {
        if (!parked) return
        if (isMock) { sdkMessage = "시연 모드입니다. 실제 길 안내를 요청하지 않았습니다."; return }
        if (sdkStatus.navigation.availability != SdkAvailability.READY) { sdkMessage = "길 안내 연결을 확인한 뒤 다시 시도해 주세요."; return }
        sdkMessage = if (runtime.navigate(WaypointConfig("travel_place", name, lat, lng)))
            "길 안내를 요청했습니다. 결과는 내비게이션에서 확인해 주세요." else "길 안내를 요청하지 못했습니다."
    }
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
    /**
     * 시연 바로가기(debug·MOCK 전용). 앞부분을 정답으로 자동 진행한 새 시연 여행으로 바꾼다.
     * LIVE 기록은 건드리지 않는다. 관광 15분은 시연 우회로 표시하고, 지난 장소의 관광 시각은 75분 간격으로 앞당겨 적는다.
     */
    fun demoJump(target: DemoTarget) {
        if (!isMock || !parked || selectedCard.story?.id != Siyeok.episode.id) return
        val flow = StoryCatalog.CURRENT_FLOW_VERSION
        val state = DemoShortcut.play(StoryCatalog.episodeFor(flow), flow, target)
        val clock = now()
        val session = UUID.randomUUID().toString()
        val visited = state.chapterIndex + if (state.phase.ordinal >= StoryPhase.EXPLORING.ordinal) 1 else 0
        val records = TourCatalog.checkpoints.take(visited).mapIndexed { i, c ->
            val back = (visited - 1 - i) * 75L * 60_000
            TourStartRecord(session, c.id, TourMode.MOCK,
                clock.copy(wallTimeMillis = clock.wallTimeMillis - back, elapsedRealtimeMillis = (clock.elapsedRealtimeMillis - back).coerceAtLeast(0)),
                debugTimeBypass = true)
        }
        val started = (records.firstOrNull()?.startedAt?.wallTimeMillis ?: clock.wallTimeMillis) - 20 * 60_000
        changeTrip(afterSave = {
            endingPanel = ""; truthDraft = emptyMap(); truthRetryMessage = null
            screen = Screen.STORY
            demoArrive()
            sdkMessage = "시연 바로가기: ${target.label} (자동 진행한 시연 기록)"
        }) {
            StoredTrip(
                mode = TourMode.MOCK, sessionId = session, chapterIndex = state.chapterIndex, phase = state.phase,
                items = state.items, clues = state.clues, startedAtEpochMillis = started, showMap = false,
                tourRecords = records, flowVersion = flow, puzzleProgress = state.puzzleProgress,
            )
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
            if (old == null || unlockTourGate() != TourGate.READY) null
            else StoryEngine.returnToCar(story!!).let {
                old.copy(phase = it.phase, items = it.items, clues = it.clues, puzzleProgress = it.puzzleProgress)
            }
        }
    }

    /** Draft changes are local and never grant a clue or write a puzzle result. */
    private fun canEditStoryPuzzle(): Boolean = parked && !saving && !loading && !casebookOpen && !showMap &&
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
        if (usesVehiclePuzzleSdk && value.isNotEmpty()) return // SDK answers only come from vehicle transitions.
        storyPuzzleDraft = storyPuzzleDraft.copy(car = value.toList()); storyPuzzleMessage = null
        if (usesVehiclePuzzleSdk) { vehiclePuzzleResetRevision++; synchronizeVehiclePuzzle(forceReset = true) }
    }
    fun changeStoryDial(value: List<Int>) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.SymbolDial ?: return
        if (!canEditStoryPuzzle() || value.size != lock.wheels.size || value.indices.any { value[it] !in lock.wheels[it].indices }) return
        storyPuzzleDraft = storyPuzzleDraft.copy(dial = value.toList()); storyPuzzleMessage = null
    }
    fun changeStorySequence(ids: List<String>) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.Sequence ?: return
        if (!canEditStoryPuzzle() || ids.size > lock.maxLength || ids.any { id -> lock.tokens.none { it.id == id } }) return
        storyPuzzleDraft = storyPuzzleDraft.copy(seq = ids.toList()); storyPuzzleMessage = null
    }
    /** 가진 소지품 가운데 아직 쓰지 않은 것만 고를 수 있다. */
    fun changeStoryItem(id: String) {
        val lock = storyPuzzle?.lock as? StoryLockDefinition.UseItem ?: return
        val s = story ?: return
        if (!canEditStoryPuzzle() || id !in lock.pool || s.inventory.none { it.id == id && !s.isItemUsed(it) }) return
        storyPuzzleDraft = storyPuzzleDraft.copy(item = id); storyPuzzleMessage = null
    }
    /** 정차 중 장면 한 줄을 음성으로 읽는다. */
    fun speakLine(text: String) { if (parked) pendingNarration = text }

    private fun draftInput(): StoryPuzzleInput? = when (storyPuzzle?.lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(storyPuzzleDraft.numbers.joinToString(""))
        is StoryLockDefinition.Directions -> StoryPuzzleInput.Directions(storyPuzzleDraft.directions.toList())
        is StoryLockDefinition.Device -> StoryPuzzleInput.Device(storyPuzzleDraft.channel, storyPuzzleDraft.level)
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(storyPuzzleDraft.choice ?: "")
        is StoryLockDefinition.Order -> StoryPuzzleInput.Order(storyPuzzleDraft.order.toList())
        is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(storyPuzzleDraft.car.toList())
        is StoryLockDefinition.SymbolDial -> StoryPuzzleInput.Dial(storyPuzzleDraft.dial.toList())
        is StoryLockDefinition.UseItem -> StoryPuzzleInput.UseItem(storyPuzzleDraft.item ?: "")
        is StoryLockDefinition.Sequence -> StoryPuzzleInput.Sequence(storyPuzzleDraft.seq.toList())
        null -> null
    }

    /** Submits a snapshot only; refresh location/safety again at the serialized mutation boundary. */
    /** 장면의 조사할 곳을 누른다. 기록할 곳이면 저장하고, 자물쇠면 자물쇠 화면을 연다. */
    fun touchHotspot(id: String) {
        val s = story ?: return
        if (!parked || s.phase != StoryPhase.PUZZLE || !s.isExplore || tourGate != TourGate.READY) return
        val spot = s.chapter.scene!!.hotspots.firstOrNull { it.id == id } ?: return
        if (!s.isHotspotVisible(spot)) return
        exploreSelected = id
        exploreFresh = if (spot.record && !s.isHotspotDone(spot)) id else null
        if (sceneGlow == id) sceneGlow = null
        storyPuzzleMessage = null
        if (spot.record && !s.isHotspotDone(spot)) {
            changeTrip { old ->
                if (old == null || story?.phase != StoryPhase.PUZZLE ||
                    unlockTourGate() != TourGate.READY) null
                else old.copy(puzzleProgress = StoryEngine.examine(story!!, id).puzzleProgress)
            }
        }
    }
    /** 장면에서 고른 곳의 자물쇠를 연다. */
    fun openHotspotLock(id: String) {
        val s = story ?: return
        val spot = s.chapter.scene?.hotspots?.firstOrNull { it.id == id } ?: return
        val stepId = s.activeLock(spot) ?: return
        if (!parked || s.phase != StoryPhase.PUZZLE || !s.isHotspotVisible(spot) || s.isStepDone(stepId)) return
        exploreFocus = stepId
        story = s.copy(exploreFocus = stepId)
        storyPuzzleDraft = StoryPuzzleDraft.forLock(story?.currentPuzzle?.lock)
        storyPuzzleMessage = null
        puzzleDraftIdentity = "${trip?.sessionId}:${s.chapterIndex}:${s.phase}:${s.currentStepIndex}:$stepId"
        synchronizeVehiclePuzzle(forceReset = true)
    }
    /** 자물쇠를 닫고 장면으로 돌아간다. */
    fun closeHotspotLock() {
        exploreFocus = null
        story = story?.copy(exploreFocus = null)
        storyPuzzleMessage = null
        synchronizeVehiclePuzzle()
    }
    /** 장면 힌트: 지금 손댈 곳 하나를 반짝인다(각성도 +1). */
    fun sceneHint() {
        val s = story ?: return
        if (!parked || s.phase != StoryPhase.PUZZLE || !s.isExplore || tourGate != TourGate.READY) return
        val target = s.actionableHotspots.firstOrNull() ?: return
        changeTrip(afterSave = { sceneGlow = target.id }) { old ->
            if (old == null || story?.phase != StoryPhase.PUZZLE ||
                unlockTourGate() != TourGate.READY) null
            else old.copy(puzzleProgress = StoryEngine.sceneHint(story!!).puzzleProgress)
        }
    }

    fun submitStoryPuzzle(input: StoryPuzzleInput) {
        refreshTour()
        if (!canEditStoryPuzzle() || input != draftInput()) return
        if (usesVehiclePuzzleSdk && storyPuzzle?.lock is StoryLockDefinition.CarSequence &&
            (carPuzzleInput as? CarPuzzleInput.Vehicle)?.ready != true) return
        val puzzleId = storyPuzzle?.id ?: return
        val submittedVehicleGeneration = runtime.puzzleGeneration
        val submittedResetRevision = vehiclePuzzleResetRevision
        val requiresVehicle = usesVehiclePuzzleSdk && storyPuzzle?.lock is StoryLockDefinition.CarSequence
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
                casebookOpen || requiresVehicle && (runtime.puzzleGeneration != submittedVehicleGeneration || vehiclePuzzleResetRevision != submittedResetRevision) ||
                unlockTourGate() != TourGate.READY) null
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
                unlockTourGate() != TourGate.READY) null
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

    /** 일반 엔딩 뒤 진상 추리를 한 번 더 푼다. 결과는 저장되고 바꿀 수 없다. */
    fun retryTruth(answers: Map<String, StoryPuzzleInput>) {
        val s = story ?: return
        if (!parked || !s.canRetryTruth) return
        var correct = false
        changeTrip(afterSave = {
            truthRetryMessage = if (correct) "진상을 밝혔습니다. 기록 한 장이 복원되었습니다." else "추리가 맞지 않았습니다. 기록은 비어 있는 채로 남습니다."
        }) { old ->
            val r = story?.let { TruthRetry.submit(it, answers) } ?: return@changeTrip null
            correct = r.second
            old?.copy(puzzleProgress = r.first.puzzleProgress)
        }
    }
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
        if (!isMock || mockVehiclePuzzleSdk || !foreground) return
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
    if (ep.prologueScene.isNotEmpty()) ep.prologueScene.map { it.text }
    else if (ep.rules.isNotEmpty()) listOf(ep.prologue) + ep.rules.map { "이야기의 규칙\n$it" }
    else if (ep.id != "siyeok") listOf(ep.prologue)
    else listOf(
        "\"요원, 브리핑을 시작한다. 1795년 윤2월, 화성에 정체불명의 역병이 돌았다.\n죽은 자가 다시 일어나는 병이다.\"",
        "\"당시 내의원이 치료제의 비방을 만들었지만, 기록은 흩어졌고 약은 끝내 쓰이지 못했다.\n그 결과 역사가 무너지고 있다.\"",
        "\"1795년으로 건너가 비방과 재료를 모으고, 백신을 완성해 전달하라.\n첫 번째 실마리는 왕의 어머니를 위한 잔치가 열린 곳에 있다.\"",
    )
