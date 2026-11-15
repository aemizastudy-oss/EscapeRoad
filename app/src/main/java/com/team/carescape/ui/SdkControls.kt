package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.team.carescape.game.TourGate
import com.team.carescape.platform.sdk.SdkAvailability

/** Configuration remains reachable before safety is known; it does not enable game actions. */
@Composable
fun SdkControls(vm: GameViewModel, onRequestPermissions: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val canOpen = vm.foreground &&
        (vm.isMock || vm.parked || vm.sdkStatus.carSafety.availability != SdkAvailability.READY)
    LaunchedEffect(canOpen, vm.foreground) {
        if (!canOpen || !vm.foreground) open = false
    }
    OutlineButton(
        if (vm.isMock) "시연 모드 · SDK 상태" else "실제 SDK · 연결 상태",
        { if (canOpen) open = true }, modifier.testTag("sdk-status-open"),
        enabled = canOpen, minHeight = 36.dp, fontSize = 13,
    )
    if (open && canOpen) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 980.dp).fillMaxWidth(.92f).heightIn(max = 620.dp)
                .background(Esc.panel.copy(alpha = 1f)).border(1.dp, Esc.gold).padding(24.dp)
                .verticalScroll(rememberScrollState()).testTag("sdk-status-sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("차량 기능 연결 상태", style = Esc.nanum(26))
            Text(if (vm.isMock) "MOCK · 개발용 시연 데이터" else "LIVE · 실제 SDK 입력", style = Esc.noto(15, Esc.gold))
            Text("연결 준비와 실제 위치 인증·길 안내·음성 출력 성공은 별도로 확인합니다.", style = Esc.noto(14, Esc.creamSoft))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("권한 확인", onRequestPermissions, Modifier.testTag("sdk-request-permissions"), enabled = !vm.loading, minHeight = 44.dp, fontSize = 15)
                OutlineButton("연결 다시 확인", vm::reconnectSdk, Modifier.testTag("sdk-reconnect"), enabled = !vm.loading && !vm.saving, minHeight = 44.dp, fontSize = 15)
                if (vm.storageError != null) OutlineButton("저장 다시 읽기", vm::retryStorage, Modifier.testTag("sdk-storage-retry"), enabled = !vm.saving, minHeight = 44.dp, fontSize = 15)
            }
            val features = listOf(
                Triple("fused", "차량 위치", vm.sdkStatus.fused),
                Triple("navigation", "길 안내", vm.sdkStatus.navigation),
                Triple("safety", "P·정지·CarUx", vm.sdkStatus.carSafety),
                Triple("vehicle", "차량 보조 상태", vm.sdkStatus.vehicle),
                Triple("tts", "음성 안내", vm.sdkStatus.tts),
                Triple("stt", "음성 인식", vm.sdkStatus.stt),
            )
            features.forEach { (id, title, feature) ->
                Column(Modifier.fillMaxWidth().border(1.dp, Esc.goldDim).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(title, style = Esc.noto(15))
                        Text(sdkAvailabilityMessage(feature.availability), Modifier.testTag("sdk-status-$id"), style = Esc.noto(15, Esc.gold))
                    }
                    feature.detail?.takeIf(String::isNotBlank)?.let { detail ->
                        Text(detail.take(160), style = Esc.noto(12, Esc.label))
                    }
                }
            }
            vm.vehiclePuzzleSignals.forEach { (control, state) ->
                Text("${control.label}: ${sdkAvailabilityMessage(state.availability)} · ${state.detail.orEmpty()}",
                    Modifier.testTag("sdk-puzzle-${control.name}"), style = Esc.noto(13, Esc.creamSoft))
            }
            Text("길 안내 상태: ${vm.sdkStatus.routeState.take(64)}", style = Esc.noto(12, Esc.label))
            Text(if (vm.sdkStatus.vehicleReceivedElapsedMillis != null) "차량 보조 표본을 수신했습니다." else "차량 보조 표본 수신은 아직 확인되지 않았습니다.", style = Esc.noto(12, Esc.label))
            Text(safetyMessage(vm), Modifier.testTag("sdk-safety-reason"), style = Esc.noto(15, Esc.cream))
            if (vm.currentWaypoint != null) {
                Text("도착 확인: ${if (vm.tourGate == TourGate.READY) "현재 챕터의 도착·관광 조건을 확인했습니다." else tourGateMessage(vm.tourStartGate)}", style = Esc.noto(14, Esc.creamSoft))
                Text("관광 후 진행: ${tourGateMessage(vm.tourGate)}", style = Esc.noto(14, Esc.creamSoft))
            }
            if (!vm.isMock && !vm.sdkStatus.locationAccuracyVerified) {
                Text("위치 정확도의 미터 단위 근거를 확인하지 못해 실제 도착 인증을 보류합니다.", Modifier.testTag("sdk-accuracy-pending"), style = Esc.noto(14, Esc.gold))
            }
            vm.sdkMessage?.let { Text(it, Modifier.testTag("sdk-message"), style = Esc.noto(14, Esc.creamSoft)) }
            vm.storageError?.let { Text(it, Modifier.testTag("sdk-storage-error"), style = Esc.noto(14, Esc.creamSoft)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlineButton("음성 중지", vm::stopAudio, Modifier.testTag("sdk-stop-audio"), minHeight = 44.dp, fontSize = 15)
                OutlineButton("닫기", { open = false }, Modifier.testTag("sdk-status-close"), minHeight = 44.dp, fontSize = 15)
            }
            Text("좌표·현재 거리·원시 위치는 화면에 표시하지 않습니다. 음성은 안전한 정차와 foreground가 확인될 때만 요청합니다.", style = Esc.noto(12, Esc.label))
        }
    }
}

@Composable
fun SdkSafetyWaitingScreen(vm: GameViewModel) {
    GoldFrame(fill = Esc.panel, modifier = Modifier.testTag("sdk-safety-paused")) {
        Column(
            Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text("안전 상태 확인", style = Esc.cormorant(22))
            Envelope()
            Text(safetyMessage(vm), style = Esc.nanum(34), textAlign = TextAlign.Center)
            Text("확인 전에는 이야기·단서·입력을 가리고 음성을 멈춥니다.", style = Esc.noto(20, Esc.creamSoft))
            if (vm.storageError != null) OutlineButton("저장 다시 읽기", vm::retryStorage, Modifier.testTag("storage-retry"), enabled = !vm.loading && !vm.saving)
            Text("연결·권한은 오른쪽 위 SDK 상태에서 확인하세요.", style = Esc.noto(15, Esc.label))
        }
    }
}

private fun safetyMessage(vm: GameViewModel): String = when {
    vm.loading -> "저장된 진행을 확인하고 있습니다"
    vm.storageError != null -> "저장 정보를 다시 확인해야 합니다"
    !vm.foreground -> "앱으로 돌아온 뒤 상태를 다시 확인해 주세요"
    !vm.parked -> "P·정지·차량 이용 허용 상태를 확인해 주세요"
    else -> "안전한 정차 상태를 확인했습니다"
}

private fun sdkAvailabilityMessage(value: SdkAvailability): String = when (value) {
    SdkAvailability.STOPPED -> "연결 전"
    SdkAvailability.CHECKING -> "확인 중"
    SdkAvailability.READY -> "연결 준비됨"
    SdkAvailability.NO_DATA -> "표본 대기"
    SdkAvailability.UNSUPPORTED -> "미지원"
    SdkAvailability.PERMISSION_DENIED -> "권한 필요"
    SdkAvailability.STALE -> "최신 표본 필요"
    SdkAvailability.ERROR -> "연결 오류"
}

fun tourRemainingMessage(remainingMillis: Long?): String = when {
    remainingMillis == null -> "관광 시작 기록을 먼저 저장해 주세요."
    remainingMillis <= 0L -> "관광 시간 조건을 채웠습니다. 안전한 정차 상태를 확인해 주세요."
    else -> "최소 15분 관광까지 약 ${(remainingMillis + 59_999L) / 60_000L}분 남았습니다."
}

fun tourGateMessage(gate: TourGate): String = when (gate) {
    TourGate.READY -> "이용 조건을 확인했습니다."
    TourGate.BACKGROUND -> "앱으로 돌아온 뒤 이용 조건을 다시 확인해 주세요."
    TourGate.NOT_PARKED -> "P 상태 확인이 필요합니다."
    TourGate.NOT_STATIONARY -> "차량이 완전히 멈춘 상태를 확인해 주세요."
    TourGate.CAR_UX_RESTRICTED -> "CarUx의 게임 이용 허용 상태를 확인해야 합니다."
    TourGate.COORDINATES_NOT_CONFIGURED -> "관광지 대표점 설정을 확인해야 합니다."
    TourGate.MODE_MISMATCH -> "진행과 입력 모드가 일치하지 않습니다."
    TourGate.RECORD_MISMATCH -> "현재 관광 시작 기록을 다시 확인해야 합니다."
    TourGate.LOCATION_MISSING -> "새로운 현재 위치를 기다리고 있습니다."
    TourGate.LOCATION_ERROR -> "위치 연결을 다시 확인해 주세요."
    TourGate.LOCATION_NOT_UPDATED -> "과거 위치 대신 새 위치 갱신이 필요합니다."
    TourGate.LOCATION_TIMESTAMP_INVALID -> "위치 표본의 시각을 확인하지 못했습니다."
    TourGate.LOCATION_STALE -> "위치 표본이 오래되어 새 갱신이 필요합니다."
    TourGate.LOCATION_INVALID -> "현재 위치 표본을 사용할 수 없습니다."
    TourGate.LOCATION_ACCURACY_UNKNOWN -> "위치 정확도 근거를 확인하지 못해 인증을 보류합니다."
    TourGate.LOCATION_INACCURATE -> "위치 오차가 커 인증을 보류합니다."
    TourGate.OUTSIDE_RADIUS -> "관광지 인증 범위 안의 현재 위치가 필요합니다."
    TourGate.TOUR_NOT_STARTED -> "관광 시작을 눌러 시작 시각을 저장해 주세요."
    TourGate.TOUR_TIME_REMAINING -> "관광 시작 후 최소 15분이 필요합니다."
    TourGate.CLOCK_INVALID -> "시간 기준을 확인하지 못했습니다."
    TourGate.CLOCK_BACKWARD -> "시각이 이전으로 바뀌어 시간 확인을 보류합니다."
    TourGate.CLOCK_SUSPICIOUS -> "시각 변경이 감지되어 시간 확인을 보류합니다."
}
