package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.team.carescape.game.DemoTarget
import com.team.carescape.game.EndingKind
import com.team.carescape.game.TourCatalog
import com.team.carescape.game.TurnSide
import com.team.carescape.model.TourMode

/** Debug source set only. Selecting LIVE never substitutes any of these mock observations. */
@Composable
fun SdkDevPanel(vm: GameViewModel, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (vm.liveSdkVehicleTest) Text(
            "LIVE SDK 테스트\n위치·15분 제외 · 테스트 기록",
            Modifier.width(340.dp).background(Color(0xF0101010)).padding(8.dp).testTag("sdk-live-test-notice"),
            style = Esc.noto(12, Color(0xFFFFD38A)),
        )
        OutlineButton(if (vm.isMock) "시연 · SIM ${if (open) "▴" else "▾"}" else "개발 모드 ${if (open) "▴" else "▾"}",
            { open = !open }, Modifier.testTag("sdk-dev-toggle"), minHeight = 36.dp, fontSize = 13)
        if (open) Column(
            Modifier.width(340.dp).heightIn(max = 580.dp).background(Color(0xF0101010))
                .border(1.dp, Color(0xFF7FB2FF)).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val canChoose = !vm.liveSdkVehicleTest && !vm.loading && !vm.saving && vm.screen == Screen.LOBBY
            Text("입력 모드 · 자동 전환 없음", style = Esc.noto(13, Color(0xFFBFD8FF)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("실제 SDK", { vm.chooseMode(TourMode.LIVE) }, Modifier.testTag("sdk-mode-live"), enabled = canChoose, minHeight = 44.dp, fontSize = 13)
                OutlineButton("시연 MOCK", { vm.chooseMode(TourMode.MOCK) }, Modifier.testTag("sdk-mode-mock"), enabled = canChoose, minHeight = 44.dp, fontSize = 13)
            }
            if (!canChoose) {
                Text("모드 선택은 로비에서 합니다. 저장된 여행은 보존합니다.", style = Esc.noto(12, Esc.label))
                OutlineButton("로비에서 모드 선택", vm::goLobby, Modifier.testTag("sdk-dev-lobby"), enabled = !vm.loading && !vm.saving, minHeight = 44.dp, fontSize = 13)
            }
            if (vm.isMock) {
                val vehicle = vm.vehicle
                val canInput = vm.parked && !vm.loading && !vm.saving
                val mockControls = canInput && !vm.mockVehiclePuzzleSdk
                OutlineButton(if (vm.mockVehiclePuzzleSdk) "차량 답안: 실제 SDK" else "차량 답안: 화면 시연",
                    { vm.setMockVehiclePuzzleSdk(!vm.mockVehiclePuzzleSdk) }, Modifier.testTag("sdk-mock-real-controls"),
                    enabled = canChoose, minHeight = 44.dp, fontSize = 13)
                if (vm.mockVehiclePuzzleSdk) Text("시연 관광 + 실제 차량 입력 · P·정지·CarUx도 실제 신호로 확인", Modifier.testTag("sdk-hybrid-label"), style = Esc.noto(12, Color(0xFFBFD8FF)))
                Text(if (vm.mockVehiclePuzzleSdk) "관광·시간은 MOCK · 차량 답안은 실제 SDK" else "시뮬레이션 데이터 · 실제 차량 신호·관광 증빙 아님", style = Esc.noto(13, Color(0xFFBFD8FF)))
                Text(if (vm.mockVehiclePuzzleSdk) "실제 차량 안전 상태: ${if (vm.parked) "P·정지·CarUx 확인" else "확인 대기"}" else if (vehicle.parked == null) "주차 상태 미확인 · P를 직접 확인하세요" else if (vehicle.parked == true) "P 시연 확인" else "주행 상태 시연", style = Esc.noto(13, Esc.creamSoft))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("P 정차", { vm.simSetParked(true) }, Modifier.testTag("sdk-sim-parked"), enabled = !vm.mockVehiclePuzzleSdk && !vm.loading && !vm.saving, minHeight = 44.dp, fontSize = 13)
                    OutlineButton("D 주행", { vm.simSetParked(false) }, Modifier.testTag("sdk-sim-driving"), enabled = !vm.mockVehiclePuzzleSdk && !vm.loading && !vm.saving, minHeight = 44.dp, fontSize = 13)
                }
                if (vm.story != null) OutlineButton("현재 장소 위치 시연", vm::demoArrive,
                    Modifier.testTag("sdk-sim-location"), enabled = canInput, minHeight = 44.dp, fontSize = 13)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("← 왼쪽", { vm.simTurnSignal(TurnSide.LEFT) }, enabled = mockControls, minHeight = 44.dp, fontSize = 13)
                    OutlineButton("오른쪽 →", { vm.simTurnSignal(TurnSide.RIGHT) }, enabled = mockControls, minHeight = 44.dp, fontSize = 13)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("전조등 ${if (vehicle.lowBeamOn == true) "켜짐" else "전환"}", vm::simToggleLowBeam, enabled = mockControls, minHeight = 44.dp, fontSize = 13)
                    OutlineButton("와이퍼 ${if (vehicle.wiperOn == true) "켜짐" else "전환"}", vm::simToggleWiper, enabled = mockControls, minHeight = 44.dp, fontSize = 13)
                }
                Text("시연 바로가기 · 앞부분을 정답으로 자동 진행한 새 시연 여행", style = Esc.noto(13, Color(0xFFBFD8FF)))
                val jump = !vm.loading && !vm.saving && vm.parked
                val n = TourCatalog.checkpoints.size
                @Composable fun JumpRow(title: String, targets: List<Pair<String, DemoTarget>>) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.width(52.dp), style = Esc.noto(12, Esc.creamSoft))
                        targets.forEach { (label, t) ->
                            OutlineButton(label, { vm.demoJump(t) }, Modifier.testTag("sdk-jump-${t.label}"), enabled = jump, minHeight = 40.dp, fontSize = 12)
                        }
                    }
                }
                JumpRow("도착", (0 until n).map { "${it + 1}" to DemoTarget.Arrive(it) })
                JumpRow("장면", (0 until n).map { "${it + 1}" to DemoTarget.Scene(it) })
                JumpRow("엔딩", listOf("공개" to DemoTarget.FinalReveal) + EndingKind.entries.map { it.label.removeSuffix(" 엔딩") to DemoTarget.Ending(it) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlineButton("배터리 모름", { vm.simSetBattery(null) }, enabled = mockControls, minHeight = 44.dp, fontSize = 12)
                    OutlineButton("30%", { vm.simSetBattery(30) }, enabled = mockControls, minHeight = 44.dp, fontSize = 12)
                    OutlineButton("80%", { vm.simSetBattery(80) }, enabled = mockControls, minHeight = 44.dp, fontSize = 12)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlineButton("주행가능 모름", { vm.simSetRange(null) }, enabled = mockControls, minHeight = 44.dp, fontSize = 12)
                    OutlineButton("20km", { vm.simSetRange(20) }, enabled = mockControls, minHeight = 44.dp, fontSize = 12)
                    OutlineButton("45km", { vm.simSetRange(45) }, enabled = mockControls, minHeight = 44.dp, fontSize = 12)
                    OutlineButton("300km", { vm.simSetRange(300) }, enabled = mockControls, minHeight = 44.dp, fontSize = 12)
                }
            } else {
                Text("LIVE에서는 모의 입력을 제공하지 않습니다. 지원·권한은 SDK 상태에서 확인하세요.", style = Esc.noto(13, Esc.creamSoft))
            }
        }
    }
}

@Composable
fun MockArrivalAction(vm: GameViewModel, modifier: Modifier = Modifier) {
    if (vm.isMock) OutlineButton("도착한 것으로 하기 (시연)", vm::demoArrive,
        modifier.testTag("sdk-demo-arrive"), enabled = vm.parked && !vm.loading && !vm.saving)
}

@Composable
fun MockTourTimeAction(vm: GameViewModel, modifier: Modifier = Modifier) {
    if (vm.isMock) OutlineButton("15분 경과 시연", vm::simElapseTour,
        modifier.testTag("sdk-demo-elapse"), enabled = vm.parked && vm.tourRemainingMillis != null && !vm.loading && !vm.saving)
}
