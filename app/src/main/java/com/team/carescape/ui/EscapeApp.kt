package com.team.carescape.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EscapeApp(vm: GameViewModel, onRequestPermissions: () -> Unit = {}) {
    val narrator = remember(vm) { Narrator(vm) }
    DisposableEffect(narrator) { onDispose { narrator.shutdown() } }

    // 시안은 1280×720 기준. 앱 창 크기에 맞춰 화면 전체를 같은 비율로 키우거나 줄인다.
    // 시안과 같은 1280×720 화면을 비율 그대로 가운데 놓고, 남는 곳은 같은 벽지로 채운다.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val scale = minOf(maxWidth.value / 1280f, maxHeight.value / 720f)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, 1f)) {
            Box(Modifier.fillMaxSize().wall(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(1280.dp, 720.dp)) { Screens(vm, narrator, onRequestPermissions) }
            }
        }
    }
}

@Composable
private fun Screens(vm: GameViewModel, narrator: Narrator, onRequestPermissions: () -> Unit) {
    // Keep a pending line until the SDK accepts it. Moving/background narration is not verified.
    LaunchedEffect(vm.pendingNarration, narrator.ready, vm.parked, vm.foreground, vm.loading, vm.storageError, vm.mode) {
        if (!vm.foreground || !vm.parked || vm.loading || vm.storageError != null) {
            narrator.stop()
        } else {
            val pending = vm.pendingNarration
            if (pending != null && narrator.ready && narrator.speak(pending) && vm.pendingNarration == pending) {
                vm.pendingNarration = null
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (!vm.foreground || !vm.parked || vm.loading || vm.storageError != null) {
            // Unknown vehicle/UX/storage state does not expose story or puzzle details.
            SdkSafetyWaitingScreen(vm)
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
                Screen.LOCK_WORKSHOP -> LockWorkshopScreen(vm)
            }
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SdkControls(vm, onRequestPermissions)
            SdkDevPanel(vm)
        }
    }
}
