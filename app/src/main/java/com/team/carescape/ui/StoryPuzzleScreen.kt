package com.team.carescape.ui

import com.team.carescape.game.CasePerson
import com.team.carescape.game.CaseItem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.CarControl
import com.team.carescape.game.LockDirection
import com.team.carescape.game.StepKind
import com.team.carescape.game.StoryCatalog
import com.team.carescape.game.StoryLockDefinition
import com.team.carescape.game.StoryPuzzleDefinition
import com.team.carescape.game.StoryPuzzleInput
import com.team.carescape.game.StoryPuzzleProgress
import com.team.carescape.game.StoryPuzzles
import com.team.carescape.game.TourGate
import com.team.carescape.ui.locks.DirectionSwipeLock
import com.team.carescape.ui.locks.NumberDialLock

/** UI drafts never carry a correct answer or write durable progress on their own. */
data class StoryPuzzleDraft(
    val numbers: List<Int> = List(4) { 0 },
    val directions: List<LockDirection> = emptyList(),
    val channel: Int = 0,
    val level: Int = 0,
    val choice: String? = null,
    val order: List<String> = emptyList(),
    val car: List<CarControl> = emptyList(),
    val dial: List<Int> = emptyList(),
    val seq: List<String> = emptyList(),
    val item: String? = null,
) {
    companion object {
        /** 숫자 자물쇠는 정답 자릿수만큼 0으로 채운다(정답 값은 담지 않는다). */
        fun forLock(lock: StoryLockDefinition?): StoryPuzzleDraft = when (lock) {
            is StoryLockDefinition.Numeric -> StoryPuzzleDraft(numbers = List(lock.answer.length) { 0 })
            is StoryLockDefinition.SymbolDial -> StoryPuzzleDraft(dial = List(lock.wheels.size) { 0 })
            else -> StoryPuzzleDraft()
        }
    }
}

/** 한 단계의 위치·각성도 같은 머리말 정보(flow3). flow2는 null. */
data class StoryStepHeader(val index: Int, val count: Int, val awakening: Int)

/** Platform and persistence wiring stays outside the sample-renderable content. */
@Composable
fun StoryPuzzleScreen(vm: GameViewModel) {
    val story = vm.story ?: return
    val definition = vm.storyPuzzle ?: return
    val caseMode = StoryCatalog.usesAwakening(story.flowVersion)
    GoldFrame(padTop = 24.dp, padH = 36.dp, padBottom = 22.dp) {
        StoryPuzzleContent(
            definition = definition,
            progress = vm.storyPuzzleProgress,
            draft = vm.storyPuzzleDraft,
            place = story.chapter.place,
            gate = vm.tourGate,
            remainingMillis = vm.tourRemainingMillis,
            enabled = vm.parked && !vm.loading && !vm.saving && vm.storageError == null,
            onNumbersChange = vm::changeStoryNumbers,
            onDirectionsChange = vm::changeStoryDirections,
            onDeviceChange = vm::changeStoryDevice,
            onSubmit = vm::submitStoryPuzzle,
            onHint = vm::showStoryPuzzleHint,
            onBack = vm::goLobby,
            modifier = Modifier.fillMaxSize(),
            message = vm.storyPuzzleMessage,
            header = if (caseMode) StoryStepHeader(story.currentStepIndex, story.steps.size, story.awakening) else null,
            onChoiceChange = vm::changeStoryChoice,
            onOrderChange = vm::changeStoryOrder,
            onCarChange = vm::changeStoryCar,
            onCasebook = if (caseMode) vm::openCasebook else null,
            onReplay = if (definition.replayNarration) vm::replayDrama else null,
            speaker = story.episode.speakerFor(definition.speaker),
            inventory = story.inventory.map { it to story.isItemUsed(it) },
            onDialChange = vm::changeStoryDial,
            onSequenceChange = vm::changeStorySequence,
            onItemChange = vm::changeStoryItem,
        )
    }
}

/**
 * Receives immutable values and callbacks only. A lost tour gate removes the prompt,
 * revealed hints and controls together; it cannot expose a parked-only puzzle.
 */
@Composable
fun StoryPuzzleContent(
    definition: StoryPuzzleDefinition,
    progress: StoryPuzzleProgress?,
    draft: StoryPuzzleDraft,
    place: String,
    gate: TourGate,
    remainingMillis: Long?,
    enabled: Boolean,
    onNumbersChange: (List<Int>) -> Unit,
    onDirectionsChange: (List<LockDirection>) -> Unit,
    onDeviceChange: (Int, Int) -> Unit,
    onSubmit: (StoryPuzzleInput) -> Unit,
    onHint: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    header: StoryStepHeader? = null,
    onChoiceChange: (String) -> Unit = {},
    onOrderChange: (List<String>) -> Unit = {},
    onCarChange: (List<CarControl>) -> Unit = {},
    onCasebook: (() -> Unit)? = null,
    onReplay: (() -> Unit)? = null,
    speaker: CasePerson? = null,
    inventory: List<Pair<CaseItem, Boolean>> = emptyList(),
    onDialChange: (List<Int>) -> Unit = {},
    onSequenceChange: (List<String>) -> Unit = {},
    onItemChange: (String) -> Unit = {},
) {
    val mayInteract by rememberUpdatedState(enabled && gate == TourGate.READY)
    val latestDraft by rememberUpdatedState(draft)
    if (gate != TourGate.READY) {
        Column(
            modifier.testTag("story-puzzle-waiting").fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text("단서 열람 조건을 다시 확인하고 있습니다", style = Esc.nanum(30), textAlign = TextAlign.Center)
            Text(place, style = Esc.noto(19, Esc.gold))
            Text(tourRemainingMessage(remainingMillis), Modifier.testTag("story-puzzle-time"), style = Esc.noto(18))
            Text(tourGateMessage(gate), Modifier.testTag("story-puzzle-gate"), style = Esc.noto(17, Esc.creamMuted), textAlign = TextAlign.Center)
            Text("관광 시간과 최신 위치·안전 상태가 확인되면 문제를 다시 엽니다.", style = Esc.noto(15, Esc.creamSoft), textAlign = TextAlign.Center)
            OutlineButton("로비로", onBack, minWidth = 180.dp)
        }
        return
    }

    val shown = (progress?.hintsShown ?: 0).coerceIn(0, definition.hints.size)
    Column(modifier.testTag("story-puzzle").fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("The Sealed Clue · $place", style = Esc.cormorant(16))
                if (header == null) {
                    Text("연동 검증용 임시 문제", Modifier.testTag("story-puzzle-preview-label"), style = Esc.noto(16, Esc.gold))
                } else {
                    Text("단계 ${header.index + 1} / ${header.count} · ${definition.kind.label}",
                        Modifier.testTag("story-step-label"), style = Esc.noto(16, Esc.gold))
                }
            }
            if (header == null) Text("정식 관광 문제는 별도 제작 예정입니다", style = Esc.noto(13, Esc.creamSoft))
            else AwakeningGauge(header.awakening.coerceAtMost(10))
            if (onCasebook != null) OutlineButton("사건 기록부", { if (enabled) onCasebook() }, Modifier.testTag("casebook-open"), enabled = enabled, minHeight = 44.dp)
            OutlineButton("로비", onBack, minHeight = 44.dp, minWidth = 90.dp)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().parchment().border(1.dp, Esc.goldDim)
                        .padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (speaker != null) SpeakerTag(speaker)
                    Text(definition.title, style = Esc.nanum(27, Esc.ink))
                    Text(definition.prompt, Modifier.testTag("story-puzzle-prompt"), style = Esc.noto(19, Esc.inkBody, lineHeight = 1.75f))
                    Text(
                        when (definition.kind) {
                            StepKind.CAR, StepKind.SCENE -> "차량 신호 연동 전에는 오른쪽 화면 버튼으로 같은 조작을 입력합니다. 정차(P) 중에만 조작하세요."
                            StepKind.VERDICT -> "진상 추리는 틀려도 다음으로 넘어가지만, 진 엔딩에는 정답이 필요합니다."
                            else -> "값을 맞춘 뒤 ‘제출’을 눌러 답을 확인하세요."
                        },
                        style = Esc.noto(14, Esc.inkSoft),
                    )
                    definition.hints.take(shown).forEachIndexed { index, hint ->
                        Column(Modifier.fillMaxWidth().border(1.dp, Esc.parchEdge).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("힌트 ${index + 1}", style = Esc.noto(14, Esc.seal))
                            Text(hint, Modifier.testTag("story-puzzle-hint-${index + 1}"), style = Esc.noto(16, Esc.inkBody))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (definition.hints.isNotEmpty()) OutlineButton(
                        if (shown < definition.hints.size) "힌트 보기 · $shown / ${definition.hints.size}" else "모든 힌트 확인",
                        { if (mayInteract && shown < definition.hints.size) onHint() },
                        Modifier.testTag("story-puzzle-hint"),
                        enabled = enabled && shown < definition.hints.size,
                        minHeight = 48.dp,
                    )
                    if (onReplay != null) OutlineButton("다시 듣기", { if (enabled) onReplay() }, Modifier.testTag("story-replay"), enabled = enabled, minHeight = 48.dp)
                    Text("오답 ${progress?.wrongAttempts ?: 0}회", Modifier.testTag("story-puzzle-wrong-count"), style = Esc.noto(15, Esc.creamSoft))
                    if (header != null && shown < definition.hints.size) {
                        Text("다음 힌트: 각성도 +${StoryPuzzles.hintCost(shown)}", style = Esc.noto(13, Esc.creamSoft))
                    }
                }
            }
            Column(
                Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val feedback: @Composable () -> Unit = {
                    Text(
                        message ?: if (enabled) "입력을 마친 뒤 제출해 주세요." else "처리 중입니다. 잠시 기다려 주세요.",
                        Modifier.testTag("story-puzzle-feedback").fillMaxWidth().padding(top = 4.dp),
                        style = Esc.noto(14, Esc.creamMuted),
                        textAlign = TextAlign.Center,
                    )
                }
                when (val lock = definition.lock) {
                    is StoryLockDefinition.Numeric -> if (lock.answer.length == 4) NumberDialLock(
                        draft.numbers,
                        { if (mayInteract) onNumbersChange(it) },
                        { if (mayInteract) onSubmit(StoryPuzzleInput.Numeric(it.joinToString(""))) },
                        modifier = Modifier.width(300.dp), enabled = enabled, feedbackContent = feedback,
                    ) else DigitStepperLock(
                        draft.numbers, enabled,
                        { if (mayInteract) onNumbersChange(it) },
                        { if (mayInteract) onSubmit(StoryPuzzleInput.Numeric(latestDraft.numbers.joinToString(""))) },
                        feedback,
                    )
                    is StoryLockDefinition.Directions -> DirectionSwipeLock(
                        draft.directions,
                        { if (mayInteract) onDirectionsChange(it) },
                        { if (mayInteract) onSubmit(StoryPuzzleInput.Directions(it)) },
                        modifier = Modifier.width(300.dp), enabled = enabled, feedbackContent = feedback,
                    )
                    is StoryLockDefinition.Device -> VirtualStoryDevice(
                        draft = draft, min = lock.min, max = lock.max, enabled = enabled,
                        onChange = { channel, level -> if (mayInteract) onDeviceChange(channel, level) },
                        onSubmit = { if (mayInteract) onSubmit(StoryPuzzleInput.Device(latestDraft.channel, latestDraft.level)) },
                        feedbackContent = feedback,
                    )
                    is StoryLockDefinition.Choice -> ChoiceLock(
                        lock, draft.choice, enabled,
                        { if (mayInteract) onChoiceChange(it) },
                        { latestDraft.choice?.let { if (mayInteract) onSubmit(StoryPuzzleInput.Choice(it)) } },
                        feedback,
                    )
                    is StoryLockDefinition.Order -> OrderLock(
                        lock, draft.order, enabled,
                        { if (mayInteract) onOrderChange(it) },
                        { if (mayInteract) onSubmit(StoryPuzzleInput.Order(latestDraft.order)) },
                        feedback,
                    )
                    is StoryLockDefinition.CarSequence -> CarSequenceLock(
                        lock, draft.car, enabled, definition.kind == StepKind.SCENE,
                        { if (mayInteract) onCarChange(it) },
                        { if (mayInteract) onSubmit(StoryPuzzleInput.CarSequence(latestDraft.car)) },
                        feedback,
                    )
                    is StoryLockDefinition.SymbolDial -> SymbolDialLock(
                        lock, draft.dial.ifEmpty { List(lock.wheels.size) { 0 } }, enabled,
                        { if (mayInteract) onDialChange(it) },
                        { if (mayInteract) onSubmit(StoryPuzzleInput.Dial(latestDraft.dial.ifEmpty { List(lock.wheels.size) { 0 } })) },
                        feedback,
                    )
                    is StoryLockDefinition.UseItem -> UseItemLock(
                        inventory, draft.item, enabled,
                        { if (mayInteract) onItemChange(it) },
                        { latestDraft.item?.let { if (mayInteract) onSubmit(StoryPuzzleInput.UseItem(it)) } },
                        feedback,
                    )
                    is StoryLockDefinition.Sequence -> SequenceLock(
                        lock, draft.seq, enabled,
                        { if (mayInteract) onSequenceChange(it) },
                        { if (mayInteract) onSubmit(StoryPuzzleInput.Sequence(latestDraft.seq)) },
                        feedback,
                    )
                }
            }
        }
    }
}

/** A local virtual control; no callbacks read or write a vehicle setting. */
@Composable
private fun VirtualStoryDevice(
    draft: StoryPuzzleDraft,
    min: Int,
    max: Int,
    enabled: Boolean,
    onChange: (Int, Int) -> Unit,
    onSubmit: () -> Unit,
    feedbackContent: @Composable () -> Unit,
) {
    require(min <= max)
    val currentDraft by rememberUpdatedState(draft)
    val canEdit by rememberUpdatedState(enabled)
    Column(
        Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Virtual Device", style = Esc.pinyon(32))
        Text("화면 속 가상 장치", style = Esc.nanum(20))
        Text("채널과 레벨 · $min ~ $max", style = Esc.noto(14, Esc.creamSoft))
        DeviceValueRow("채널", draft.channel, min, max, enabled, "story-device-channel") { amount ->
            if (canEdit) onChange((currentDraft.channel + amount).coerceIn(min, max), currentDraft.level)
        }
        DeviceValueRow("레벨", draft.level, min, max, enabled, "story-device-level") { amount ->
            if (canEdit) onChange(currentDraft.channel, (currentDraft.level + amount).coerceIn(min, max))
        }
        Text("실제 차량 설정은 변경하지 않습니다.", style = Esc.noto(13, Esc.creamSoft), textAlign = TextAlign.Center)
        ParchmentButton("제출", { if (canEdit) onSubmit() }, Modifier.testTag("story-puzzle-submit"), enabled = enabled, minWidth = 180.dp, minHeight = 48.dp)
        feedbackContent()
    }
}

@Composable
private fun DeviceValueRow(label: String, value: Int, min: Int, max: Int, enabled: Boolean, tag: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = Esc.noto(16))
        OutlineButton("−", { if (enabled && value > min) onStep(-1) }, Modifier.testTag("$tag-minus"), enabled = enabled && value > min, minWidth = 44.dp, minHeight = 48.dp)
        Text(value.toString(), Modifier.width(24.dp).testTag(tag), style = Esc.cormorant(25), textAlign = TextAlign.Center)
        OutlineButton("+", { if (enabled && value < max) onStep(1) }, Modifier.testTag("$tag-plus"), enabled = enabled && value < max, minWidth = 44.dp, minHeight = 48.dp)
    }
}
