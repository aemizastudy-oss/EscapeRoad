package com.example.roadescape.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.roadescape.BuildConfig
import com.example.roadescape.R
import com.example.roadescape.game.*
import com.example.roadescape.model.*

private enum class ConfirmKind { NEW_GAME, ANSWER, SKIP }
private data class Confirmation(val kind: ConfirmKind, val sessionId: String = "", val puzzleId: String = "")

@Composable
fun RoadEscapeApp(viewModel: GameViewModel, state: GameUiState) {
    val nav = rememberNavController()
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    val requestNewGame: () -> Unit = {
        if (state.session == null && state.error == null) viewModel.newGameConfirmed()
        else confirmation = Confirmation(ConfirmKind.NEW_GAME)
    }
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { destination ->
            nav.navigate(destination) { popUpTo("start") { inclusive = false }; launchSingleTop = true }
        }
    }
    LaunchedEffect(state.canReadContent, state.session?.sessionId) {
        if (!state.canReadContent && confirmation?.kind != ConfirmKind.NEW_GAME) confirmation = null
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            AppHeader(state)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.session != null && state.error == null) {
                    EscapeButton(stringResource(R.string.checkpoints), "tab-checkpoints", onClick = { nav.navigate("checkpoints") { launchSingleTop = true } }, secondary = true)
                    EscapeButton(stringResource(R.string.clues), "tab-clues", onClick = { nav.navigate("clues") { launchSingleTop = true } }, secondary = true)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmation = Confirmation(ConfirmKind.NEW_GAME) }, enabled = !state.saving, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.new_game)) }
                }
            }
            EnvironmentPanel(viewModel, state)
            if (state.saving) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("saving"))
                Text(stringResource(R.string.saving), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 4.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                val showGame = !state.loading && state.error == null
                // Keep the graph installed even while loading or repairing a corrupt first save.
                NavHost(nav, startDestination = "start") {
                        composable("start") { if (showGame) StartScreen(state, requestNewGame) { nav.navigate("checkpoints") } }
                        composable("checkpoints") {
                            if (!showGame) return@composable
                            CheckpointScreen(state, onOpen = { puzzleId ->
                                val session = state.session
                                if (session != null) viewModel.command(GameCommand.StartPuzzle(session.sessionId, puzzleId))
                                nav.navigate("puzzle/$puzzleId") { launchSingleTop = true }
                            }, onCompletion = { nav.navigate("completion") })
                        }
                        composable("puzzle/{puzzleId}") { entry ->
                            if (!showGame) return@composable
                            val puzzleId = entry.arguments?.getString("puzzleId").orEmpty()
                            PuzzleScreen(state, puzzleId, viewModel::command,
                                onAnswer = { confirmation = Confirmation(ConfirmKind.ANSWER, state.session?.sessionId.orEmpty(), puzzleId) },
                                onSkip = { confirmation = Confirmation(ConfirmKind.SKIP, state.session?.sessionId.orEmpty(), puzzleId) },
                                onNext = { nav.navigate("checkpoints") { popUpTo("checkpoints"); launchSingleTop = true } })
                        }
                        composable("clues") { if (showGame) ClueListScreen(state) { nav.navigate("clue/$it") } }
                        composable("clue/{clueId}") { entry -> if (showGame) ClueDetailScreen(state, entry.arguments?.getString("clueId").orEmpty()) { nav.popBackStack() } }
                        composable("completion") { if (showGame) CompletionScreen(state, requestNewGame) }
                }
                when {
                    state.loading -> CenterMessage(stringResource(R.string.loading), loading = true)
                    state.error != null -> ErrorScreen(state.error, viewModel::retry, requestNewGame)
                }
            }
        }
    }
    confirmation?.let { pending ->
        val title = when (pending.kind) { ConfirmKind.NEW_GAME -> R.string.confirm_new_title; ConfirmKind.ANSWER -> R.string.confirm_answer_title; ConfirmKind.SKIP -> R.string.confirm_skip_title }
        val body = when (pending.kind) { ConfirmKind.NEW_GAME -> R.string.confirm_new_body; ConfirmKind.ANSWER -> R.string.confirm_answer_body; ConfirmKind.SKIP -> R.string.confirm_skip_body }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(stringResource(title)) }, text = { Text(stringResource(body)) },
            confirmButton = { TextButton(modifier = Modifier.heightIn(min = 56.dp).testTag("confirmation-confirm"), onClick = {
                confirmation = null
                when (pending.kind) {
                    ConfirmKind.NEW_GAME -> viewModel.newGameConfirmed()
                    ConfirmKind.ANSWER -> viewModel.command(GameCommand.RevealAnswer(pending.sessionId, pending.puzzleId))
                    ConfirmKind.SKIP -> viewModel.command(GameCommand.Skip(pending.sessionId, pending.puzzleId))
                }
            }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { confirmation = null }, modifier = Modifier.heightIn(min = 56.dp).testTag("confirmation-cancel")) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun AppHeader(state: GameUiState) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        val branding: @Composable () -> Unit = {
            Column {
                Text(stringResource(R.string.brand), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.brand_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val badges: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(if (BuildConfig.DEBUG) R.string.simulation_badge else R.string.release_badge), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(parkingLabel(state.parking)), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (maxWidth >= 620.dp) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { branding(); badges() }
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { branding(); badges() }
    }
}

@Composable
private fun StartScreen(state: GameUiState, onNew: () -> Unit, onResume: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(stringResource(R.string.package_caption), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.start_title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.start_description), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(max = 820.dp))
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.content?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.start_features), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.session?.let { session -> Text(stringResource(R.string.saved_progress, session.progress.count { it.status.isComplete }, session.progress.size)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EscapeButton(stringResource(R.string.start_new), "start-new", onNew, enabled = !state.saving)
                    if (state.session != null) EscapeButton(stringResource(R.string.resume), "resume", onResume, secondary = true)
                }
            }
        }
        Text(stringResource(R.string.simulation_notice), color = MaterialTheme.colorScheme.secondary, modifier = Modifier.widthIn(max = 880.dp))
    }
}

@Composable
private fun CheckpointScreen(state: GameUiState, onOpen: (String) -> Unit, onCompletion: () -> Unit) {
    val content = state.content ?: return
    val session = state.session ?: return
    val current = GameController.currentCheckpoint(content, session)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.board_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.board_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.progress_count, session.progress.count { it.status.isComplete }, content.checkpoints.size), color = MaterialTheme.colorScheme.primary)
        content.checkpoints.forEachIndexed { index, checkpoint ->
            val progress = session.progress.first { it.checkpointId == checkpoint.id }
            val accessible = checkpoint.id == current?.id || progress.status.isComplete
            Surface(shape = MaterialTheme.shapes.medium, color = if (checkpoint.id == current?.id) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.checkpoint_index, index + 1), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(checkpoint.title, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(statusLabel(progress.status)))
                    Text(stringResource(if (checkpoint.id == current?.id) {
                        if (progress.arrivedAt == null) R.string.checkpoint_not_arrived else R.string.checkpoint_arrived
                    } else if (progress.status.isComplete) R.string.checkpoint_arrived else R.string.checkpoint_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EscapeButton(stringResource(R.string.checkpoint_open), "checkpoint-${checkpoint.id}", { onOpen(checkpoint.puzzleId) }, accessible && !state.saving, secondary = checkpoint.id != current?.id)
                }
            }
        }
        if (current == null) EscapeButton(stringResource(R.string.view_completion), "view-completion", onCompletion)
    }
}

@Composable
private fun PuzzleScreen(state: GameUiState, puzzleId: String, command: (GameCommand) -> Unit, onAnswer: () -> Unit, onSkip: () -> Unit, onNext: () -> Unit) {
    val content = state.content ?: return
    val session = state.session ?: return
    val puzzle = content.puzzles.find { it.id == puzzleId } ?: run { CenterMessage(stringResource(R.string.not_found)); return }
    val checkpoint = content.checkpoints.first { it.puzzleId == puzzleId }
    val progress = session.progress.first { it.checkpointId == checkpoint.id }
    val gate = GameController.gate(content, session, state.environment, puzzleId)
    LaunchedEffect(session.sessionId, puzzleId, gate, progress.status) {
        if (gate == GateReason.READY && progress.status == CheckpointStatus.NOT_STARTED) {
            command(GameCommand.StartPuzzle(session.sessionId, puzzleId))
        }
    }
    if (!state.canReadContent || (gate != GateReason.READY && gate != GateReason.BUSY && !progress.status.isComplete)) { PausedScreen(gate); return }
    if (progress.status.isComplete) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.puzzle_complete), style = MaterialTheme.typography.headlineMedium)
            Text(checkpoint.title)
            Text(stringResource(statusLabel(progress.status)), color = MaterialTheme.colorScheme.primary)
            Feedback(state.feedback, state.rejectedGate)
            EscapeButton(stringResource(R.string.next_checkpoint), "next-checkpoint", onNext)
        }
        return
    }
    val enabled = gate == GateReason.READY && !state.saving
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val details: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(checkpoint.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(puzzle.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(puzzle.prompt, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.wrong_attempts, progress.wrongAttempts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Feedback(state.feedback, state.rejectedGate)
                HorizontalDivider()
                Text(stringResource(R.string.hint_count, progress.revealedHintCount, puzzle.hints.size), modifier = Modifier.testTag("hint-count"))
                puzzle.hints.take(progress.revealedHintCount).forEachIndexed { index, text ->
                    Column(Modifier.testTag("hint-content")) { Text(stringResource(R.string.hint_step, index + 1), color = MaterialTheme.colorScheme.secondary); Text(text) }
                }
                if (progress.answerRevealed) Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(16.dp).testTag("answer-content"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.answer_title), fontWeight = FontWeight.Bold)
                        Text(answerText(puzzle.lock), color = MaterialTheme.colorScheme.primary)
                        Text(puzzle.solution)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EscapeButton(stringResource(R.string.hint_next), "hint-next", { command(GameCommand.RevealHint(session.sessionId, puzzleId, progress.revealedHintCount + 1)) }, enabled && progress.revealedHintCount < puzzle.hints.size, secondary = true)
                    EscapeButton(stringResource(R.string.reveal_answer), "reveal-answer", onAnswer, enabled && !progress.answerRevealed, secondary = true)
                    EscapeButton(stringResource(R.string.skip), "skip", onSkip, enabled, secondary = true)
                }
            }
        }
        val lock: @Composable () -> Unit = {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    LockControls(puzzle, session.sessionId, enabled) { input -> command(GameCommand.Submit(session.sessionId, puzzleId, input)) }
                }
            }
        }
        if (maxWidth >= 1000.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) { details() }
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) { lock() }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) { details(); lock(); Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun LockControls(puzzle: PuzzleDefinition, sessionId: String, enabled: Boolean, onSubmit: (LockInput) -> Unit) {
    var digits by rememberSaveable(sessionId, puzzle.id) { mutableStateOf("") }
    var directions by rememberSaveable(sessionId, puzzle.id) { mutableStateOf(listOf<String>()) }
    var channel by rememberSaveable(sessionId, puzzle.id) { mutableIntStateOf(0) }
    var level by rememberSaveable(sessionId, puzzle.id) { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (val lock = puzzle.lock) {
            is LockDefinition.Numeric -> {
                Text(stringResource(R.string.lock_numeric), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.numeric_length, lock.length), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(digits.padEnd(lock.length, '–'), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("numeric-input"))
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(0)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { digit -> EscapeButton(digit.toString(), "digit-$digit", { if (digits.length < lock.length) digits += digit }, enabled, modifier = Modifier.weight(1f), secondary = true) }
                        if (row.size == 1) Spacer(Modifier.weight(2f))
                    }
                }
            }
            is LockDefinition.Sequence -> {
                Text(stringResource(R.string.lock_sequence), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.sequence_length, lock.answer.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (directions.isEmpty()) stringResource(R.string.empty_input) else directions.joinToString("  ") { directionSymbol(Direction.valueOf(it)) }, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("sequence-input"))
                Direction.entries.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { direction -> EscapeButton(stringResource(directionLabel(direction)), "sequence-${direction.name}", { if (directions.size < lock.answer.size) directions = directions + direction.name }, enabled, Modifier.weight(1f), secondary = true) }
                } }
            }
            is LockDefinition.StateCombination -> {
                Text(stringResource(R.string.lock_combination), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.virtual_device), color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.channel), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (lock.min..lock.max).forEach { value -> EscapeButton(value.toString(), "channel-$value", { channel = value }, enabled, secondary = channel != value) }
                }
                Text(stringResource(R.string.level), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (lock.min..lock.max).forEach { value -> EscapeButton(value.toString(), "level-$value", { level = value }, enabled, secondary = level != value) }
                }
            }
        }
        EscapeButton(stringResource(R.string.submit), "puzzle-submit", {
            onSubmit(when (puzzle.lock) {
                is LockDefinition.Numeric -> LockInput.Numeric(digits)
                is LockDefinition.Sequence -> LockInput.Sequence(directions.map(Direction::valueOf))
                is LockDefinition.StateCombination -> LockInput.StateCombination(mapOf("channel" to channel, "level" to level))
            })
        }, enabled, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (puzzle.lock !is LockDefinition.StateCombination) EscapeButton(stringResource(R.string.delete), "puzzle-delete", {
                digits = digits.dropLast(1); directions = directions.dropLast(1)
            }, enabled, Modifier.weight(1f), secondary = true)
            EscapeButton(stringResource(R.string.clear), "puzzle-clear", { digits = ""; directions = emptyList(); channel = 0; level = 0 }, enabled, Modifier.weight(1f), secondary = true)
        }
    }
}

@Composable
private fun ClueListScreen(state: GameUiState, onOpen: (String) -> Unit) {
    if (!state.canReadContent) { PausedScreen(readGate(state)); return }
    val content = state.content ?: return
    val acquired = state.session?.acquiredClues.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.clues), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.clues_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (acquired.isEmpty()) {
            Text(stringResource(R.string.empty_clues_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.empty_clues_body))
        }
        acquired.forEach { item ->
            val clue = content.clues.first { it.id == item.clueId }
            val checkpoint = content.checkpoints.first { it.id == item.sourceCheckpointId }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(clue.title, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.clue_source, checkpoint.title))
                    Text(stringResource(if (item.acquisitionType == AcquisitionType.SOLVED) R.string.clue_acquired_solved else R.string.clue_acquired_skipped), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EscapeButton(clue.title, "clue-${clue.id}", { onOpen(clue.id) }, secondary = true)
                }
            }
        }
    }
}

@Composable
private fun ClueDetailScreen(state: GameUiState, clueId: String, onBack: () -> Unit) {
    if (!state.canReadContent) { PausedScreen(readGate(state)); return }
    val content = state.content ?: return
    val acquired = state.session?.acquiredClues?.find { it.clueId == clueId }
    val clue = content.clues.find { it.id == clueId }
    if (acquired == null || clue == null) { CenterMessage(stringResource(R.string.not_found)); return }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(clue.title, style = MaterialTheme.typography.headlineLarge)
        Text(clue.body, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.clue_source, content.checkpoints.first { it.id == acquired.sourceCheckpointId }.title))
        Text(stringResource(if (acquired.acquisitionType == AcquisitionType.SOLVED) R.string.clue_acquired_solved else R.string.clue_acquired_skipped))
        EscapeButton(stringResource(R.string.back_to_clues), "back-to-clues", onBack, secondary = true)
    }
}

@Composable
private fun CompletionScreen(state: GameUiState, onNew: () -> Unit) {
    if (!state.canReadContent) { PausedScreen(readGate(state)); return }
    val session = state.session ?: return
    val content = state.content ?: return
    if (session.progress.any { !it.status.isComplete }) { CenterMessage(stringResource(R.string.board_description)); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("completion"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.completion_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.completion_description))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(R.string.summary_solved, session.progress.count { it.status == CheckpointStatus.SOLVED }, "summary-solved")
            SummaryCard(R.string.summary_skipped, session.progress.count { it.status == CheckpointStatus.SKIPPED }, "summary-skipped")
            SummaryCard(R.string.summary_hints, session.progress.sumOf { it.revealedHintCount }, "summary-hints")
            SummaryCard(R.string.summary_answers, session.progress.count { it.answerRevealed }, "summary-answers")
        }
        content.checkpoints.forEach { checkpoint ->
            val progress = session.progress.first { it.checkpointId == checkpoint.id }
            Text(stringResource(R.string.summary_item, checkpoint.title, stringResource(statusLabel(progress.status)), progress.revealedHintCount, stringResource(if (progress.answerRevealed) R.string.yes else R.string.no)))
        }
        EscapeButton(stringResource(R.string.new_game), "start-new", onNew, enabled = !state.saving)
    }
}

@Composable
private fun SummaryCard(@StringRes label: Int, count: Int, tag: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.widthIn(min = 150.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(count.toString(), style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun PausedScreen(gate: GateReason) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp).testTag("paused"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(gateLabel(gate)), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.pause_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorScreen(error: GameUiError, onRetry: () -> Unit, onReset: () -> Unit) {
    val description = when {
        error.contentError -> R.string.error_package
        error.reason == "CORRUPT" -> R.string.error_corrupt
        error.reason == "UNSUPPORTED_SCHEMA" -> R.string.error_schema
        error.reason == "CONTENT_MISMATCH" || error.reason == "INVALID_PROGRESS" -> R.string.error_content
        else -> R.string.error_io
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(if (error.contentError) "startup-error" else "storage-error"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(if (error.contentError) R.string.content_error else R.string.storage_error), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(description), color = MaterialTheme.colorScheme.error)
        EscapeButton(stringResource(R.string.retry), "retry", onRetry)
        if (!error.contentError) EscapeButton(stringResource(R.string.new_game), "error-reset", onReset, secondary = true)
    }
}

@Composable
private fun CenterMessage(message: String, loading: Boolean = false) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
        if (loading) CircularProgressIndicator()
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Feedback(feedback: GameFeedback?, gate: GateReason?) {
    val message = when (feedback) {
        GameFeedback.INVALID_INPUT -> R.string.feedback_invalid
        GameFeedback.INCORRECT -> R.string.feedback_incorrect
        GameFeedback.CORRECT -> R.string.feedback_correct
        GameFeedback.ARRIVED -> R.string.feedback_arrived
        GameFeedback.HINT_REVEALED -> R.string.feedback_hint
        GameFeedback.ANSWER_REVEALED -> R.string.feedback_answer
        GameFeedback.SKIPPED -> R.string.feedback_skip
        GameFeedback.BLOCKED -> gate?.let(::gateLabel)
        else -> null
    }
    if (message != null) Text(stringResource(message), color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("puzzle-feedback"))
}

@Composable
fun EscapeButton(text: String, tag: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, secondary: Boolean = false) {
    val buttonModifier = modifier.heightIn(min = 56.dp).widthIn(min = 56.dp).testTag(tag)
    if (secondary) OutlinedButton(onClick, buttonModifier, enabled = enabled, shape = MaterialTheme.shapes.small) { Text(text) }
    else Button(onClick, buttonModifier, enabled = enabled, shape = MaterialTheme.shapes.small) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@StringRes fun parkingLabel(parking: ParkingState): Int = when (parking) { ParkingState.PARKED -> R.string.parking_parked; ParkingState.DRIVING -> R.string.parking_driving; ParkingState.UNKNOWN -> R.string.parking_unknown }
@StringRes private fun statusLabel(status: CheckpointStatus): Int = when (status) { CheckpointStatus.NOT_STARTED -> R.string.status_not_started; CheckpointStatus.IN_PROGRESS -> R.string.status_in_progress; CheckpointStatus.SOLVED -> R.string.status_solved; CheckpointStatus.SKIPPED -> R.string.status_skipped }
@StringRes fun gateLabel(gate: GateReason): Int = when (gate) {
    GateReason.READY -> R.string.gate_ready; GateReason.BACKGROUND -> R.string.gate_background; GateReason.PARKING_UNKNOWN -> R.string.gate_unknown
    GateReason.DRIVING -> R.string.gate_driving; GateReason.STORAGE_ERROR -> R.string.gate_storage; GateReason.BUSY -> R.string.gate_busy
    GateReason.ARRIVAL_REQUIRED -> R.string.gate_arrival; GateReason.COMPLETED -> R.string.gate_completed; GateReason.NOT_CURRENT -> R.string.gate_not_current; GateReason.STALE_EVENT -> R.string.gate_stale
}
private fun readGate(state: GameUiState) = when { !state.foreground -> GateReason.BACKGROUND; state.parking == ParkingState.DRIVING -> GateReason.DRIVING; else -> GateReason.PARKING_UNKNOWN }
@StringRes private fun directionLabel(direction: Direction): Int = when (direction) { Direction.UP -> R.string.direction_up; Direction.RIGHT -> R.string.direction_right; Direction.DOWN -> R.string.direction_down; Direction.LEFT -> R.string.direction_left }
private fun directionSymbol(direction: Direction): String = when (direction) { Direction.UP -> "↑"; Direction.RIGHT -> "→"; Direction.DOWN -> "↓"; Direction.LEFT -> "←" }
private fun answerText(lock: LockDefinition): String = when (lock) {
    is LockDefinition.Numeric -> lock.answer
    is LockDefinition.Sequence -> lock.answer.joinToString(" ") { directionSymbol(it) }
    is LockDefinition.StateCombination -> "channel = ${lock.channel}, level = ${lock.level}"
}
