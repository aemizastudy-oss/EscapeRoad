package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.carescape.game.TurnSide

@Composable
fun EscapeApp(vm: GameViewModel) {
    val context = LocalContext.current
    val narrator = remember { Narrator(context) }
    DisposableEffect(Unit) { onDispose { narrator.shutdown() } }

    // 시안은 1280×720 기준. 앱 창 크기에 맞춰 화면 전체를 같은 비율로 키우거나 줄인다.
    // 시안과 같은 1280×720 화면을 비율 그대로 가운데 놓고, 남는 곳은 같은 벽지로 채운다.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val scale = minOf(maxWidth.value / 1280f, maxHeight.value / 720f)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, 1f)) {
            Box(Modifier.fillMaxSize().wall(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(1280.dp, 720.dp)) { Screens(vm, narrator) }
            }
        }
    }
}

@Composable
private fun Screens(vm: GameViewModel, narrator: Narrator) {
    // 이동 중 음성 드라마는 주행 중에도 들려야 하므로 화면과 상관없이 여기서 읽는다.
    LaunchedEffect(vm.pendingNarration) {
        vm.pendingNarration?.let { narrator.speak(it); vm.pendingNarration = null }
    }
    Box(Modifier.fillMaxSize()) {
        if (!vm.parked) {
            // 안전: 정차가 확인되지 않으면 어떤 화면에서든 주행 중 화면으로 덮는다.
            val game = vm.game
            val story = vm.story
            if (vm.screen == Screen.PLAY || vm.screen == Screen.SOLVED) {
                DrivingScreen(game?.let { it.currentPuzzle?.title ?: "진상 확인" })
            } else {
                StoryDrivingScreen(story?.takeIf { vm.screen == Screen.STORY })
            }
        } else {
            when (vm.screen) {
                Screen.LOBBY -> LobbyScreen(vm)
                Screen.THEME_SELECT -> ThemeSelectScreen(vm)
                Screen.BRIEFING -> StoryBriefingScreen(vm)
                Screen.PLAY -> PuzzleScreen(vm, narrator)
                Screen.SOLVED -> SolvedScreen(vm)
                Screen.SUCCESS -> SuccessScreen(vm)
                Screen.STORY_BRIEFING -> StoryBriefingScreen(vm)
                Screen.STORY_PROLOGUE -> StoryPrologueScreen(vm)
                Screen.STORY -> StoryScreen(vm, narrator)
            }
        }
        SimPanel(vm, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
    }
}

/**
 * 시연 전용 패널: 실제 차량 신호 대신 손으로 차량 상태를 바꾼다(시뮬레이션 데이터).
 * 실차 화면에는 넣지 않는다. 접어 두면 작은 'SIM' 표식만 남는다.
 */
@Composable
private fun SimPanel(vm: GameViewModel, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val v = vm.vehicle
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            if (open) "SIM 닫기 ▴" else "SIM ▾",
            Modifier
                .heightIn(min = 32.dp)
                .background(Color(0xE6101010))
                .border(1.dp, Color(0xFF7FB2FF))
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            style = Esc.body.copy(fontSize = 13.sp, color = Color(0xFFBFD8FF)),
        )
        if (!open) return@Column
        Column(
            Modifier.width(300.dp).background(Color(0xF0101010)).border(1.dp, Color(0xFF7FB2FF)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("시뮬레이션 데이터 · 실제 차량 신호 아님", style = Esc.body.copy(fontSize = 13.sp, color = Color(0xFFBFD8FF)))
            SimRow("기어") {
                SimButton("P 정차", v.parked == true) { vm.simSetParked(true) }
                SimButton("D 주행", v.parked != true) { vm.simSetParked(false) }
            }
            SimRow("깜빡이") {
                SimButton("← 왼쪽", false) { vm.simTurnSignal(TurnSide.LEFT) }
                SimButton("오른쪽 →", false) { vm.simTurnSignal(TurnSide.RIGHT) }
            }
            SimRow("전조등") { SimButton(if (v.lowBeamOn == true) "켜짐" else "꺼짐", v.lowBeamOn == true, vm::simToggleLowBeam) }
            SimRow("와이퍼") { SimButton(if (v.wiperOn == true) "켜짐" else "꺼짐", v.wiperOn == true, vm::simToggleWiper) }
            SimRow("배터리") {
                SimButton("모름", v.batteryPercent == null) { vm.simSetBattery(null) }
                SimButton("30%", v.batteryPercent == 30) { vm.simSetBattery(30) }
                SimButton("80%", v.batteryPercent == 80) { vm.simSetBattery(80) }
            }
        }
    }
}

@Composable
private fun SimRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.width(56.dp), style = Esc.body.copy(fontSize = 13.sp, color = Color(0xFFDDDDDD)))
        content()
    }
}

@Composable
private fun SimButton(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .heightIn(min = 44.dp)
            .background(if (active) Color(0xFF2F5C99) else Color(0xFF2A2A2A))
            .border(1.dp, Color(0xFF7FB2FF))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        style = Esc.body.copy(fontSize = 13.sp, color = Color.White, lineHeight = 16.sp),
    )
}
