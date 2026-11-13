package com.team.carescape.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.team.carescape.game.Feedback
import com.team.carescape.game.GameState
import com.team.carescape.game.PuzzleEngine
import com.team.carescape.game.StoryEngine
import com.team.carescape.game.StoryEpisode
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.StoryState
import com.team.carescape.game.ThemeCard
import com.team.carescape.game.ThemeCatalog
import com.team.carescape.game.TurnSide
import com.team.carescape.game.VehicleState

enum class Screen { LOBBY, THEME_SELECT, BRIEFING, PLAY, SOLVED, SUCCESS, STORY_BRIEFING, STORY_PROLOGUE, STORY }

/**
 * 화면 흐름과 퍼즐 엔진을 잇는다. 차량 값은 지금은 시연용 패널(시뮬레이션 데이터)에서만 들어온다.
 * 실제 Pleos Vehicle SDK 어댑터는 아직 연결하지 않았다.
 */
class GameViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.LOBBY)
        private set
    var selectedCard by mutableStateOf<ThemeCard>(ThemeCatalog.cards.first())
        private set
    var game by mutableStateOf<GameState?>(null)
        private set

    /** 시연용 차량 상태. 기본은 'P단 정차 · 배터리 모름'. */
    var vehicle by mutableStateOf(VehicleState(parked = true))
        private set

    /** 퍼즐 시작 시각(ms). 화면 상단 경과 시간 표시용. */
    var startedAtMs by mutableStateOf(0L)
        private set

    /** 정차가 확인되지 않으면 어떤 단서·조작 화면도 보여 주지 않는다. */
    val parked: Boolean get() = vehicle.parked == true

    fun goLobby() { screen = Screen.LOBBY }
    fun goThemeSelect() { screen = Screen.THEME_SELECT }
    fun select(card: ThemeCard) { if (card.playable) selectedCard = card }
    fun goBriefing() {
        if (!selectedCard.playable) return
        screen = if (selectedCard.story != null) Screen.STORY_BRIEFING else Screen.BRIEFING
    }

    // ---- 스토리형 테마 (시역) ----

    var story by mutableStateOf<StoryState?>(null)
        private set

    /** 이동 중 음성 드라마를 들려줄 때 부른다. 화면에서 Narrator로 읽는다. */
    var pendingNarration by mutableStateOf<String?>(null)

    /** 프롤로그 내레이션 쪽 번호(0부터). */
    var prologuePage by mutableStateOf(0)
        private set

    /** 다음 목적지로 떠나기 전에 로드트립 지도를 보여 주는 중인지. */
    var showMap by mutableStateOf(false)
        private set

    /** 수사를 시작한 시각(ms). 엔딩의 누적 수사 시간 표시용. */
    var storyStartedAtMs by mutableStateOf(0L)
        private set

    fun goStoryBriefing() { if (selectedCard.story != null) screen = Screen.STORY_BRIEFING }

    /** 브리핑의 "시간 이동 시작": 프롤로그 내레이션으로. */
    fun goPrologue() {
        val ep = selectedCard.story ?: return
        prologuePage = 0
        pendingNarration = prologuePagesOf(ep).first()
        screen = Screen.STORY_PROLOGUE
    }

    fun prologueNext() {
        val ep = selectedCard.story ?: return
        val pages = prologuePagesOf(ep)
        if (prologuePage < pages.lastIndex) {
            prologuePage++
            pendingNarration = pages[prologuePage]
        } else startStory()
    }

    fun startStory() {
        val ep = selectedCard.story ?: return
        story = StoryEngine.start(ep)
        storyStartedAtMs = System.currentTimeMillis()
        showMap = true
        screen = Screen.STORY
    }

    /** 지도의 "다음 장소 길 안내": 지도를 닫고 이동 중 음성 드라마를 시작한다. */
    fun startGuidance() {
        showMap = false
        story?.let { pendingNarration = it.chapter.travelDrama }
    }

    fun resumeStory() { if (story != null) screen = Screen.STORY }
    fun demoArrive() = storyStep { StoryEngine.demoArrive(it, vehicle.parked) }
    fun leaveCar() = storyStep { StoryEngine.leaveCar(it) }
    fun returnToCar() = storyStep { StoryEngine.returnToCar(it) }
    fun storyNext() {
        storyStep { StoryEngine.next(it) }
        val s = story ?: return
        if (s.phase == StoryPhase.EN_ROUTE) showMap = true
    }
    fun replayDrama() { story?.let { pendingNarration = it.chapter.travelDrama } }
    fun endStory() { story = null; showMap = false; screen = Screen.LOBBY }
    fun restartStory() { story = null; showMap = false; screen = Screen.STORY_BRIEFING }

    private fun storyStep(step: (StoryState) -> StoryState) { story = story?.let(step) }

    fun startCase() {
        val episode = selectedCard.episode ?: return
        game = PuzzleEngine.onVehicle(PuzzleEngine.start(episode), vehicle)
        startedAtMs = System.currentTimeMillis()
        screen = Screen.PLAY
    }

    fun resume() { if (game != null) screen = if (game!!.finished) Screen.SUCCESS else Screen.PLAY }

    fun submitCode(input: String) { advance { PuzzleEngine.submitCode(it, input) } }
    fun submitChoice(index: Int) { advance { PuzzleEngine.submitChoice(it, index) } }
    fun showHint() { advance { PuzzleEngine.showHint(it) } }

    /** 해금 연출을 닫고 다음 퍼즐(또는 탈출 성공)로. */
    fun continueAfterSolved() {
        screen = if (game?.finished == true) Screen.SUCCESS else Screen.PLAY
    }

    fun abandon() {
        game = null
        screen = Screen.LOBBY
    }

    // ---- 시연용 차량 조작 (시뮬레이션 데이터) ----

    fun simSetParked(parked: Boolean) = updateVehicle(vehicle.copy(parked = parked))
    fun simToggleLowBeam() = updateVehicle(vehicle.copy(lowBeamOn = vehicle.lowBeamOn != true))
    fun simToggleWiper() = updateVehicle(vehicle.copy(wiperOn = vehicle.wiperOn != true))
    fun simSetBattery(percent: Int?) = updateVehicle(vehicle.copy(batteryPercent = percent))

    /** 레버를 한 번 튕긴 것처럼: 켜짐 → 꺼짐 두 번의 신호를 보낸다. */
    fun simTurnSignal(side: TurnSide) {
        updateVehicle(vehicle.copy(turnSignal = side))
        updateVehicle(vehicle.copy(turnSignal = null))
    }

    private fun updateVehicle(next: VehicleState) {
        vehicle = next
        if (screen == Screen.STORY) storyStep { StoryEngine.onParked(it, next.parked) }
        advance { PuzzleEngine.onVehicle(it, next) }
    }

    private fun advance(step: (GameState) -> GameState) {
        val before = game ?: return
        if (screen != Screen.PLAY) {
            // 해금 연출 중이나 다른 화면에서도 차량 상태는 계속 반영하되, 퍼즐 입력으로 쓰지 않는다.
            game = before.copy(lastVehicle = vehicle)
            return
        }
        val after = step(before)
        game = after
        if (after.feedback == Feedback.CORRECT && after.puzzleIndex > before.puzzleIndex) {
            screen = Screen.SOLVED
        }
    }
}

/** 프롤로그를 시안처럼 세 쪽으로 나눈다. */
fun prologuePagesOf(ep: StoryEpisode): List<String> =
    if (ep.id != "siyeok") listOf(ep.prologue)
    else listOf(
        "\"요원, 브리핑을 시작한다. 1795년 윤2월, 화성에 정체불명의 역병이 돌았다.\n죽은 자가 다시 일어나는 병이다.\"",
        "\"당시 내의원이 치료제의 비방을 만들었지만, 기록은 흩어졌고 약은 끝내 쓰이지 못했다.\n그 결과 역사가 무너지고 있다.\"",
        "\"1795년으로 건너가 비방과 재료를 모으고, 백신을 완성해 전달하라.\n첫 번째 실마리는 왕의 어머니를 위한 잔치가 열린 곳에 있다.\"",
    )
