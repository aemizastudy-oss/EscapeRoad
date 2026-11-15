package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Mode and position actions remain guarded by actual vehicle safety in the ViewModel. */
@Composable
fun ReviewTourControls(vm: GameViewModel, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(vm.parked, vm.foreground) {
        if (!vm.parked || !vm.foreground) open = false
    }
    OutlineButton(
        if (vm.reviewTour) "체험 · 위치 시연 / 실제 15분" else "실행 모드",
        { open = true },
        modifier.testTag("review-mode-open"),
        enabled = vm.parked && !vm.saving && !vm.loading && !vm.liveSdkVehicleTest,
        minHeight = 36.dp,
        fontSize = 13,
    )
    if (open && vm.parked) {
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            ReviewTourDialogContent(
                review = vm.reviewTour,
                canChooseMode = vm.canChooseReviewTour,
                canSetLocation = vm.canSetReviewLocation,
                locationMessage = vm.reviewLocationMessage,
                onChooseMode = { vm.chooseReviewTour(it); open = false },
                onSetLocation = vm::setReviewLocation,
                onClose = { open = false },
                modifier = Modifier.widthIn(max = 720.dp).heightIn(max = 620.dp),
            )
        }
    }
}

/** Plain content keeps layout and callbacks testable without the app/SDK graph. */
@Composable
internal fun ReviewTourDialogContent(
    review: Boolean,
    canChooseMode: Boolean,
    canSetLocation: Boolean,
    locationMessage: String,
    onChooseMode: (Boolean) -> Unit,
    onSetLocation: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(Esc.panel).border(1.dp, Esc.gold).padding(24.dp)
            .verticalScroll(rememberScrollState()).testTag("review-mode-dialog"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("실행 모드", style = Esc.nanum(26))
        Text(if (review) "현재: 관광 위치 시연 + 실제 차량 SDK" else "현재: 기본 실행 모드", style = Esc.noto(17, Esc.gold))
        Text("관광 위치 시연은 목적지 좌표를 직접 입력하는 체험입니다. 500m 반경 검사와 실제 15분 관광 대기는 유지합니다. P·정지·이용 허용 상태와 차량 퍼즐 조작은 실제 SDK를 사용합니다.", style = Esc.noto(15, Esc.creamSoft, lineHeight = 1.5f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("실제 위치 · LIVE", { onChooseMode(false) }, Modifier.testTag("live-mode-select"), enabled = canChooseMode && review, minWidth = 240.dp, minHeight = 48.dp, fontSize = 16)
            ParchmentButton("관광 위치 시연", { onChooseMode(true) }, Modifier.testTag("review-mode-select"), enabled = canChooseMode && !review, minWidth = 240.dp, minHeight = 48.dp, fontSize = 16)
        }
        if (!canChooseMode) Text("실행 모드는 안전하게 정차한 뒤 로비에서 변경할 수 있습니다.", style = Esc.noto(14, Esc.label))
        if (review) {
            Text("시연 위치 입력", style = Esc.nanum(20))
            Text(locationMessage, Modifier.testTag("review-location-message"), style = Esc.noto(14, Esc.creamSoft))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParchmentButton("목적지 위치 입력", { onSetLocation(true) }, Modifier.testTag("review-location-inside"), enabled = canSetLocation, minWidth = 240.dp, minHeight = 48.dp, fontSize = 16)
                OutlineButton("반경 밖 위치 입력", { onSetLocation(false) }, Modifier.testTag("review-location-outside"), enabled = canSetLocation, minWidth = 240.dp, minHeight = 48.dp, fontSize = 16)
            }
            Text("이동 장면에서 위치를 입력한 뒤 ‘관광 시작’을 눌러야 15분이 시작됩니다. 위치가 오래되면 다시 입력해 주세요. 시연 위치는 실제 관광지 방문을 인증하지 않습니다.", style = Esc.noto(14, Esc.label, lineHeight = 1.5f))
        } else {
            Text("LIVE는 실제 위치를 사용합니다. 현재 Pleos SDK의 미터 정확도 값이 확인되지 않아 최초 관광 시작이 보류될 수 있습니다.", style = Esc.noto(14, Esc.label, lineHeight = 1.5f))
        }
        OutlineButton("닫기", onClose, Modifier.testTag("review-mode-close"), minHeight = 44.dp, fontSize = 15)
    }
}
