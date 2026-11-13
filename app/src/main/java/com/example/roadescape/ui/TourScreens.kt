package com.example.roadescape.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roadescape.BuildConfig
import com.example.roadescape.game.GameController
import com.example.roadescape.game.TourCatalog
import com.example.roadescape.game.TourGate
import com.example.roadescape.game.TourPolicy
import com.example.roadescape.model.TourMode
import com.example.roadescape.platform.sdk.PleosSdkStatus
import com.example.roadescape.platform.sdk.SdkAvailability
import com.example.roadescape.platform.sdk.SdkFeatureState

@Composable
fun TourSetupScreen(
    state: GameUiState,
    onStart: (TourMode) -> Unit,
    onPermissions: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-tour-setup"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CaseHeading("A Journey Through Suwon", "수원 관광 여행", side = "5 CHAPTERS")
        Text("관광을 마치고 차로 돌아오면 다음 자물쇠를 엽니다.", color = CaseColors.MutedGold)
        Text("이번 이야기 · 시역(屍疫), 물을 건너지 못하는 자들 — 1795년 화성에 퍼진 역병을 막을 백신의 재료와 비방을 물길을 따라 모읍니다.", color = CaseColors.Cream)
        PaperSheet(Modifier.fillMaxWidth()) {
            Text("이번 여행의 다섯 장소", color = CaseColors.Burgundy, style = MaterialTheme.typography.titleLarge)
            TourCatalog.checkpoints.forEachIndexed { index, waypoint ->
                Text("${index + 1}. ${waypoint.name}", color = CaseColors.Ink, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(color = CaseColors.Gold)
            Text("관광지 도착 확인 → 관광 시작 → 15분 이상 관광 → 차량 복귀 후 위치·주차 재확인 → 퍼즐", color = CaseColors.Ink)
            Text("각 장소에서 ‘관광 시작’을 누른 시각을 기기에 저장합니다. 앱이나 차량 전원이 꺼져 있어도 재실행 후 경과 시간을 확인합니다.", color = CaseColors.Ink)
            Text("관광지 대표점 반경 500m는 모든 장소에 같은 임시 설정입니다. 지정 주차장은 없으며 합법적인 주차 위치를 직접 확인해야 합니다.", color = CaseColors.Ink)
            Text("만석공원과 광교호수공원의 실제 방문 구역·최종 인증 중심점은 아직 확정하지 않았습니다. 공개 대표점은 현장 검증이 필요한 초기값입니다.", color = CaseColors.Burgundy)
        }
        CasePanel(Modifier.fillMaxWidth()) {
            Text("실제 차량 데이터로 여행", color = CaseColors.Gold, style = MaterialTheme.typography.titleLarge)
            Text("Pleos의 최신 위치와 게임 이용이 허용되는 주차 상태를 확인합니다. 권한이나 위치 정확도 근거가 확보되지 않으면 관광·퍼즐 인증을 보류합니다.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EscapeButton("권한 확인", "tour-setup-permissions", onPermissions, secondary = true)
                EscapeButton("실제 여행 시작", "tour-start-live", { onStart(TourMode.LIVE) }, enabled = !state.loading && !state.saving)
            }
        }
        if (BuildConfig.DEBUG) {
            CasePanel(Modifier.fillMaxWidth()) {
                Text("개발용 관광 시연", color = CaseColors.Gold, style = MaterialTheme.typography.titleLarge)
                Text("도착 위치와 15분 경과를 모의 입력합니다. 실제 방문·차량 SDK 수신을 확인한 기록으로 사용하지 않습니다.")
                EscapeButton("시연 데이터로 시작", "tour-start-mock", { onStart(TourMode.MOCK) }, enabled = !state.loading && !state.saving, secondary = true)
            }
        }
        Text("여행을 시작하면 새 진행을 만듭니다. 기존 테스트 게임이 있으면 초기화 확인을 먼저 진행합니다.", color = CaseColors.MutedGold)
        EscapeButton("돌아가기", "tour-setup-back", onBack, secondary = true)
    }
}

@Composable
fun TourProgressPanel(viewModel: GameViewModel, state: GameUiState) {
    val content = state.content ?: return
    if (!content.requiresTour) return
    val session = state.session ?: return
    val current = GameController.currentCheckpoint(content, session)
    CasePanel(Modifier.fillMaxWidth().testTag("tour-progress")) {
        Text(
            if (state.mode == TourMode.LIVE) "관광 여행 · 실제 데이터" else "관광 여행 · 개발용 시뮬레이션",
            color = CaseColors.Gold,
            style = MaterialTheme.typography.labelLarge,
        )
        if (current == null) {
            Text("다섯 경유지의 진행을 모두 저장했습니다.")
            return@CasePanel
        }
        Text(current.title, style = MaterialTheme.typography.titleMedium)
        Text(tourGateMessage(state.tourGate), modifier = Modifier.testTag("tour-gate"), color = CaseColors.MutedGold)
        if (state.canReadContent) ChapterStoryBlock(state, current)
        if (state.tourError != null) {
            Text("관광 기록을 읽거나 저장하지 못했습니다. 기존 기록을 보존하고 진행을 잠시 멈췄습니다.", modifier = Modifier.testTag("tour-storage-error"))
            EscapeButton("저장 다시 확인", "tour-retry", viewModel::retry, enabled = !state.saving && !state.loading, secondary = true)
        }
        if (state.canReadContent) {
            if (state.tourRecord == null) {
                Text("차에서 내리기 전에 관광 시작을 눌러 주세요. 저장 완료 후 자유롭게 관광하고 돌아오세요.")
            } else {
                Text("관광 시작 기록을 기기에 저장했습니다.", modifier = Modifier.testTag("tour-start-record"))
                Text(tourTimeMessage(state), modifier = Modifier.testTag("tour-time-remaining"), color = CaseColors.Gold)
                Text("관광 후 돌아오면 최신 위치와 주차 상태를 다시 확인합니다. 시동 OFF·ON만으로 퍼즐이 열리지는 않습니다.", style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EscapeButton(
                    if (state.tourRecord == null) "관광 시작" else "복귀 위치 재확인",
                    "tour-start",
                    viewModel::startTour,
                    enabled = state.tourStartGate == TourGate.READY &&
                        state.tourError == null && !state.saving && !state.loading,
                )
                if (state.mode == TourMode.LIVE) {
                    EscapeButton("현재 관광지 길 안내", "tour-navigate", viewModel::navigateCurrent,
                        enabled = !state.saving && state.sdk.navigation.availability == SdkAvailability.READY,
                        secondary = true)
                }
            }
        } else {
            Text("안전한 정차와 게임 이용 허용 상태가 확인되면 관광 상세를 표시합니다.", modifier = Modifier.testTag("tour-details-paused"))
        }
        if (BuildConfig.DEBUG && state.mode == TourMode.MOCK) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EscapeButton("도착 위치 시연", "tour-location-inside", { viewModel.simulateTourLocation(true) },
                    enabled = !state.saving && !state.loading, secondary = true)
                EscapeButton("범위 밖 위치 시연", "tour-location-outside", { viewModel.simulateTourLocation(false) },
                    enabled = !state.saving && !state.loading, secondary = true)
                EscapeButton("15분 경과 시연", "tour-debug-elapsed", viewModel::debugTourElapsed,
                    enabled = state.canReadContent && state.tourRecord != null && state.tourError == null && !state.saving,
                    secondary = true)
            }
        }
    }
}

/**
 * 챕터 이야기(시역). 정차·게임 허용 상태에서만 부른다. 진행 판정에는 관여하지 않는다.
 * 첫 장소 출발 전 → 프롤로그, 도착 전 → 가는 길의 이야기, 도착 확인 후 → 도착 이야기와 둘러볼 곳.
 */
@Composable
private fun ChapterStoryBlock(state: GameUiState, current: com.example.roadescape.model.CheckpointDefinition) {
    val content = state.content ?: return
    val chapter = current.story ?: return
    val story = content.story
    val first = content.checkpoints.firstOrNull()?.id == current.id
    PaperSheet(Modifier.fillMaxWidth().testTag("chapter-story")) {
        if (story != null && first && state.tourRecord == null) {
            Text("${story.title}, ${story.subtitle}", color = CaseColors.Burgundy, style = MaterialTheme.typography.titleLarge)
            Text(story.prologue, color = CaseColors.Ink, modifier = Modifier.testTag("story-prologue"))
            story.rules.forEach { Text("· $it", color = CaseColors.Ink) }
            HorizontalDivider(color = CaseColors.Gold)
        }
        when {
            state.tourRecord != null -> {
                Text("둘러볼 곳", color = CaseColors.Burgundy, fontWeight = FontWeight.Bold)
                Text(chapter.explore ?: chapter.arrival, color = CaseColors.Ink)
            }
            state.tourStartGate == TourGate.READY -> {
                Text("도착", color = CaseColors.Burgundy, fontWeight = FontWeight.Bold)
                Text(chapter.arrival, color = CaseColors.Ink, modifier = Modifier.testTag("chapter-arrival"))
                chapter.explore?.let { Text(it, color = CaseColors.Burgundy) }
            }
            else -> {
                Text("가는 길의 이야기", color = CaseColors.Burgundy, fontWeight = FontWeight.Bold)
                Text(chapter.travelDrama, color = CaseColors.Ink, modifier = Modifier.testTag("chapter-travel"))
                Text("이동 중에는 화면을 보지 말고 운전에 집중해 주세요. 이 이야기는 정차 중에만 표시합니다.", color = CaseColors.Ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
            }
        }
        chapter.item?.let { Text("이 장소에서 얻을 재료 · $it", color = CaseColors.Burgundy) }
    }
}

@Composable
fun TourVoiceControls(viewModel: GameViewModel, state: GameUiState) {
    if (state.mode != TourMode.LIVE || !state.canReadContent || state.tourGate != TourGate.READY) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EscapeButton("현재 문제 읽기", "tour-speak", viewModel::speakPrompt,
            enabled = state.tourGate == TourGate.READY && !state.saving && state.sdk.tts.availability == SdkAvailability.READY,
            secondary = true)
        EscapeButton("답안 음성 입력", "tour-listen", viewModel::listenAnswer,
            enabled = state.tourGate == TourGate.READY && !state.saving && state.sdk.stt.availability == SdkAvailability.READY && !state.sdk.listening,
            secondary = true)
        if (state.sdk.speaking || state.sdk.listening) EscapeButton("음성 중지", "tour-stop-audio", viewModel::stopAudio, secondary = true)
    }
    state.voiceDraft?.takeIf { it.isNotBlank() }?.let { draft ->
        Text("인식한 답안: ${draft.take(120)}", modifier = Modifier.testTag("tour-voice-draft"))
        Text("내용을 확인하고 제출을 눌러 주세요. 인식 결과는 자동 제출하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        EscapeButton("이 답안으로 제출", "tour-voice-submit", viewModel::submitVoiceAnswer,
            enabled = state.tourGate == TourGate.READY && !state.saving)
    }
}

@Composable
fun SdkStatusScreen(
    viewModel: GameViewModel,
    state: GameUiState,
    onPermissions: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-sdk-status"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CaseHeading("Connections Before the Journey", "차량 기능 연결 상태")
        Text("연결 준비와 실제 값 수신은 서로 다른 상태입니다. 지원되지 않거나 수신하지 못한 기능을 연결 성공으로 표시하지 않습니다.", color = CaseColors.MutedGold)
        Text(if (state.mode == TourMode.LIVE) "현재 진행은 실제 데이터 모드입니다." else "현재 진행은 시뮬레이션 모드입니다. 아래 연결 상태가 시연 기록을 실제 방문 기록으로 바꾸지는 않습니다.")
        val features = listOf(
            Triple("fused", "차량 위치", state.sdk.fused),
            Triple("navigation", "길 안내", state.sdk.navigation),
            Triple("car-safety", "주차·주행 제한", state.sdk.carSafety),
            Triple("vehicle", "차량 상태", state.sdk.vehicle),
            Triple("tts", "음성 안내", state.sdk.tts),
            Triple("stt", "음성 입력", state.sdk.stt),
        )
        features.forEach { (id, title, feature) ->
            CasePanel(Modifier.fillMaxWidth()) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(sdkFeatureMessage(id, feature, state.sdk), modifier = Modifier.testTag("sdk-status-$id"), color = CaseColors.Gold)
            }
        }
        Text(
            if (state.sdk.locationAccuracyVerified) "위치 정확도 기준이 확인됐습니다. 최신 위치와 주차 상태는 관광지마다 다시 확인합니다."
            else "위치 정확도의 근거가 확인되지 않아 실제 위치 인증을 보류합니다. 연결 준비나 과거 위치만으로 도착을 인증하지 않습니다.",
            modifier = Modifier.testTag("sdk-location-verification"),
            color = CaseColors.MutedGold,
        )
        state.sdkMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.testTag("sdk-message"), color = CaseColors.Gold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EscapeButton("권한 확인", "sdk-request-permissions", onPermissions)
            if (state.sdk.speaking || state.sdk.listening) EscapeButton("음성 중지", "sdk-stop-audio", viewModel::stopAudio, secondary = true)
            EscapeButton("돌아가기", "sdk-status-back", onBack, secondary = true)
        }
        Text("좌표·현재 거리·원시 차량 신호는 이 화면에 표시하거나 진행 기록에 저장하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = CaseColors.MutedGold)
    }
}

private fun sdkFeatureMessage(id: String, feature: SdkFeatureState, sdk: PleosSdkStatus): String =
    when (feature.availability) {
        SdkAvailability.STOPPED -> "연결 전"
        SdkAvailability.CHECKING -> "확인 중"
        SdkAvailability.READY -> when {
            id == "vehicle" && sdk.vehicleReceivedElapsedMillis != null -> "차량 값 수신"
            id == "tts" && sdk.speaking -> "음성 안내 중"
            id == "stt" && sdk.listening -> "음성 입력 중"
            else -> "준비됨"
        }
        SdkAvailability.NO_DATA -> "수신 대기"
        SdkAvailability.UNSUPPORTED -> "미지원"
        SdkAvailability.PERMISSION_DENIED -> "권한 필요"
        SdkAvailability.STALE -> "최신 값 확인 필요"
        SdkAvailability.ERROR -> "연결 오류"
    }

private fun tourTimeMessage(state: GameUiState): String {
    if (state.tourRecord?.debugTimeBypass == true) return "개발용 15분 경과 시연을 적용했습니다."
    val remaining = (TourPolicy.MIN_TOUR_MILLIS - state.tourElapsedMillis.coerceAtLeast(0L)).coerceAtLeast(0L)
    return if (remaining == 0L) "관광 시간 조건을 채웠습니다. 위치·주차 재확인 후 퍼즐을 엽니다."
    else "최소 관광 시간까지 약 ${(remaining + 59_999L) / 60_000L}분 남았습니다."
}

fun tourGateMessage(gate: TourGate): String = when (gate) {
    TourGate.READY -> "이용 조건을 확인했습니다."
    TourGate.BACKGROUND -> "앱으로 돌아온 뒤 주차와 위치를 다시 확인해 주세요."
    TourGate.NOT_PARKED -> "안전한 주차 상태 확인이 필요합니다."
    TourGate.NOT_STATIONARY -> "차량이 완전히 멈춘 상태를 확인해 주세요."
    TourGate.CAR_UX_RESTRICTED -> "현재 차량의 게임 이용 허용 상태를 확인할 수 없습니다."
    TourGate.COORDINATES_NOT_CONFIGURED -> "관광지 대표점 설정이 확인되지 않았습니다."
    TourGate.MODE_MISMATCH -> "진행과 입력 데이터의 모드가 일치하지 않습니다."
    TourGate.RECORD_MISMATCH -> "현재 경유지의 관광 기록을 다시 확인해야 합니다."
    TourGate.LOCATION_MISSING -> "현재 위치 수신을 기다리고 있습니다."
    TourGate.LOCATION_ERROR -> "위치를 확인하지 못했습니다. 연결 상태를 확인해 주세요."
    TourGate.LOCATION_NOT_UPDATED -> "과거 위치 대신 새로 갱신된 위치가 필요합니다."
    TourGate.LOCATION_TIMESTAMP_INVALID -> "위치의 수신 시각을 확인하지 못했습니다."
    TourGate.LOCATION_STALE -> "최신 위치를 다시 확인해 주세요."
    TourGate.LOCATION_INVALID -> "수신된 위치를 사용할 수 없습니다."
    TourGate.LOCATION_ACCURACY_UNKNOWN -> "위치 정확도를 확인하지 못해 인증을 보류합니다."
    TourGate.LOCATION_INACCURATE -> "위치가 충분히 정확하지 않아 인증을 보류합니다."
    TourGate.OUTSIDE_RADIUS -> "현재 관광지의 인증 범위에 도착하지 않았습니다."
    TourGate.TOUR_NOT_STARTED -> "도착·주차 확인 후 관광 시작을 눌러 주세요."
    TourGate.TOUR_TIME_REMAINING -> "관광 시작 후 최소 15분이 지나야 퍼즐을 열 수 있습니다."
    TourGate.CLOCK_INVALID -> "시간 기준을 확인하지 못해 관광 인증을 보류합니다."
    TourGate.CLOCK_BACKWARD -> "시각이 이전으로 변경되어 관광 시간을 다시 확인해야 합니다."
    TourGate.CLOCK_SUSPICIOUS -> "시각 변경이 감지되어 관광 시간을 확정할 수 없습니다."
}
