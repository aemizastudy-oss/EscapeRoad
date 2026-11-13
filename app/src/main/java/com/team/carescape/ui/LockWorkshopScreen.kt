package com.team.carescape.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.team.carescape.game.DialColor
import com.team.carescape.game.LockDirection
import com.team.carescape.ui.locks.ColorDialLock
import com.team.carescape.ui.locks.DirectionSwipeLock
import com.team.carescape.ui.locks.EnglishDialLock
import com.team.carescape.ui.locks.NumberDialLock

enum class WorkshopLock(val label: String) {
    COLOR("색상 자물쇠"), DIRECTION("방향 자물쇠"), ENGLISH("영어 자물쇠"), NUMBER("숫자 자물쇠"),
}

/** Temporary practice input. It is deliberately separate from saved story progress. */
data class LockWorkshopState(
    val selected: WorkshopLock = WorkshopLock.COLOR,
    val colors: List<DialColor> = List(4) { DialColor.RED },
    val directions: List<LockDirection> = emptyList(),
    val english: String = "",
    val numbers: List<Int> = List(4) { 0 },
    val submitted: String? = null,
)

@Composable
fun LockWorkshopScreen(vm: GameViewModel) {
    BackHandler(onBack = vm::goLobby)
    LockWorkshopContent(
        state = vm.lockWorkshop,
        onBack = vm::goLobby,
        onSelect = vm::selectWorkshopLock,
        onColorsChange = vm::changeWorkshopColors,
        onDirectionsChange = vm::changeWorkshopDirections,
        onEnglishChange = vm::changeWorkshopEnglish,
        onNumbersChange = vm::changeWorkshopNumbers,
        onSubmitColors = vm::submitWorkshopColors,
        onSubmitDirections = vm::submitWorkshopDirections,
        onSubmitEnglish = vm::submitWorkshopEnglish,
        onSubmitNumbers = vm::submitWorkshopNumbers,
        enabled = vm.parked && !vm.saving,
    )
}

/** Plain UI: no ViewModel, SDK, storage, or platform runtime is needed to render sample state. */
@Composable
fun LockWorkshopContent(
    state: LockWorkshopState,
    onBack: () -> Unit,
    onSelect: (WorkshopLock) -> Unit,
    onColorsChange: (List<DialColor>) -> Unit,
    onDirectionsChange: (List<LockDirection>) -> Unit,
    onEnglishChange: (String) -> Unit,
    onNumbersChange: (List<Int>) -> Unit,
    onSubmitColors: (List<DialColor>) -> Unit,
    onSubmitDirections: (List<LockDirection>) -> Unit,
    onSubmitEnglish: (String) -> Unit,
    onSubmitNumbers: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier.fillMaxSize().testTag("lock-workshop")) {
        GoldFrame(padTop = 42.dp, padH = 42.dp, padBottom = 24.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(54.dp)) {
                Column(
                    Modifier.width(280.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlineButton("로비로", onBack, minWidth = 120.dp, minHeight = 46.dp)
                    Text("The Lock Workshop", style = Esc.pinyon(30))
                    Text("자물쇠 체험", style = Esc.nanum(32))
                    Text("입력을 마친 뒤 제출을 눌러 주세요.", style = Esc.noto(16, Esc.creamMuted, lineHeight = 1.6f))
                    WorkshopLock.entries.forEach { kind ->
                        if (kind == state.selected) {
                            ParchmentButton(kind.label, { if (enabled) onSelect(kind) },
                                modifier = Modifier.fillMaxWidth(), enabled = enabled, minHeight = 48.dp)
                        } else {
                            OutlineButton(kind.label, { if (enabled) onSelect(kind) },
                                modifier = Modifier.fillMaxWidth(), enabled = enabled, minHeight = 48.dp)
                        }
                    }
                    Text("입력 연습용입니다.\n이야기 진행에는 반영되지 않습니다.", style = Esc.noto(14, Esc.creamSoft, lineHeight = 1.6f))
                }
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    val feedback: @Composable () -> Unit = {
                        Text(
                            state.submitted?.let { "제출한 답: $it" } ?: "제출한 답이 없습니다",
                            Modifier.fillMaxWidth().testTag("lock-submitted-answer")
                                .heightIn(max = 84.dp).verticalScroll(rememberScrollState())
                                .background(Esc.panel).border(1.dp, Esc.goldDim).padding(12.dp),
                            style = Esc.noto(16, Esc.cream),
                        )
                    }
                    val lockModifier = Modifier.width(324.dp)
                    when (state.selected) {
                        WorkshopLock.COLOR -> ColorDialLock(state.colors, onColorsChange, onSubmitColors,
                            lockModifier, enabled, feedback)
                        WorkshopLock.DIRECTION -> DirectionSwipeLock(state.directions, onDirectionsChange, onSubmitDirections,
                            lockModifier, enabled, feedback)
                        WorkshopLock.ENGLISH -> EnglishDialLock(state.english, onEnglishChange, onSubmitEnglish,
                            lockModifier, enabled, feedbackContent = feedback)
                        WorkshopLock.NUMBER -> NumberDialLock(state.numbers, onNumbersChange, onSubmitNumbers,
                            lockModifier, enabled, feedback)
                    }
                }
            }
        }
    }
}
