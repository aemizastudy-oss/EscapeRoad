package com.example.roadescape.ui

import com.example.roadescape.R
import com.example.roadescape.model.TourMode

import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.Direction
import com.example.roadescape.model.GamePackage
import com.example.roadescape.model.GameSession
import com.example.roadescape.model.LockDefinition
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun CaseSolutionScreen(state: GameUiState, onHome: () -> Unit, onRating: () -> Unit) {
    if (!state.canReadContent) { CasePausedScreen(state); return }
    val content = state.content
    val session = state.session
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-solution"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CaseHeading("The Solution", "사건 해설")
        if (content == null || session == null || !isComplete(content, session)) {
            CasePanel {
                Text("모든 경유지를 처리하면 해설이 열립니다.", fontFamily = CaseFonts.Serif, color = CaseColors.Cream)
                Text("아직 완료하지 않은 퍼즐의 정답은 여기에 표시하지 않습니다.", color = CaseColors.MutedGold)
            }
            EscapeButton("사건으로 돌아가기", "solution-home", onHome)
            return@Column
        }
        Column(Modifier.testTag("solution-content"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            content.checkpoints.forEachIndexed { index, checkpoint ->
                val puzzle = content.puzzles.first { it.id == checkpoint.puzzleId }
                val progress = session.progress.first { it.checkpointId == checkpoint.id }
                PaperSheet {
                    Text("CASE ${index + 1}  /  ${checkpoint.title}", color = CaseColors.Burgundy, fontFamily = CaseFonts.Serif, fontWeight = FontWeight.Bold)
                    Text(puzzle.title, color = CaseColors.Ink, style = MaterialTheme.typography.headlineSmall)
                    Text(extraAnswerText(puzzle.lock), color = CaseColors.Burgundy, style = MaterialTheme.typography.headlineMedium)
                    Text(puzzle.solution, color = CaseColors.Ink, style = MaterialTheme.typography.bodyLarge)
                    Text(if (progress.status == CheckpointStatus.SOLVED) "결과 · 직접 해결" else "결과 · 건너뛰기", color = CaseColors.Burgundy)
                    Text("힌트 ${progress.revealedHintCount}단계 · 오답 ${progress.wrongAttempts}회 · 정답 공개 ${if (progress.answerRevealed) "사용" else "미사용"}", color = CaseColors.Ink)
                    val rewards = session.acquiredClues.filter { it.sourceCheckpointId == checkpoint.id }
                    if (rewards.isNotEmpty()) Text("획득 단서 · " + rewards.joinToString { acquired -> content.clues.first { it.id == acquired.clueId }.title }, color = CaseColors.Ink)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EscapeButton(stringResource(R.string.nav_lobby_return), "solution-home", onHome, secondary = true)
            EscapeButton("기록 남기기", "solution-rating", onRating)
        }
    }
}

@Composable
fun CaseNotebookScreen(state: GameUiState, onBack: () -> Unit) {
    if (!state.canReadContent) { CasePausedScreen(state); return }
    val content = state.content ?: return
    val session = state.session ?: return
    val context = LocalContext.current
    val notebook = remember(context) { LocalCaseNotes(context) }
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable(session.sessionId) { mutableStateOf(content.checkpoints.first().id) }
    val drafts = remember(session.sessionId) {
        mutableStateMapOf<String, String>().apply {
            content.checkpoints.forEach { put(it.id, notebook.readNote(session.sessionId, it.id)) }
        }
    }
    val saved = remember(session.sessionId) { mutableStateMapOf<String, String>().apply { putAll(drafts) } }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var clearId by remember { mutableStateOf<String?>(null) }
    val page = content.checkpoints.first { it.id == selectedId }
    val editor: @Composable () -> Unit = {
        PaperSheet {
            Text(page.title, color = CaseColors.Burgundy, style = MaterialTheme.typography.titleLarge, fontFamily = CaseFonts.Serif)
            Text("단서를 정리하는 텍스트 수첩", color = CaseColors.Ink)
            OutlinedTextField(
                value = drafts[selectedId].orEmpty(),
                onValueChange = { if (it.length <= 4000) { drafts[selectedId] = it; message = null } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).testTag("notebook-input"),
                placeholder = { Text("떠오른 생각과 기억할 단서를 적어 두세요.") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = CaseFonts.Serif),
                colors = paperTextFieldColors(),
            )
            Text(if (drafts[selectedId] == saved[selectedId]) "기기에 저장된 메모" else "저장하지 않은 수정 내용이 있습니다.", color = CaseColors.Burgundy)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EscapeButton("메모 저장", "notebook-save", {
                    val checkpointId = selectedId
                    val text = drafts[checkpointId].orEmpty()
                    busy = true
                    scope.launch {
                        val success = notebook.saveNote(session.sessionId, checkpointId, text)
                        if (success) saved[checkpointId] = text
                        message = if (success) "이 기기에 메모를 저장했습니다." else "메모를 저장하지 못했습니다. 다시 시도해 주세요."
                        busy = false
                    }
                }, !busy)
                EscapeButton("현재 메모 지우기", "notebook-clear", { clearId = selectedId }, !busy, secondary = true)
            }
            Text("‘메모 저장’을 누르면 재실행 후에도 복원됩니다. 게임 진행과 정답 기록은 바뀌지 않습니다.", color = CaseColors.Ink, style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, color = CaseColors.Burgundy, modifier = Modifier.testTag("notebook-status")) }
        }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("screen-notebook"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CaseHeading("The Detective's Notebook", "탐정 수첩")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pages", color = CaseColors.Gold, fontFamily = CaseFonts.Serif, fontSize = 22.sp)
                    content.checkpoints.forEachIndexed { index, checkpoint ->
                        EscapeButton("${index + 1}  ${checkpoint.title}", "notebook-page-${checkpoint.id}", { selectedId = checkpoint.id; message = null }, !busy, Modifier.fillMaxWidth(), secondary = selectedId != checkpoint.id)
                    }
                }
                Box(Modifier.weight(1f)) { editor() }
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content.checkpoints.forEachIndexed { index, checkpoint ->
                        EscapeButton("페이지 ${index + 1}", "notebook-page-${checkpoint.id}", { selectedId = checkpoint.id; message = null }, !busy, secondary = selectedId != checkpoint.id)
                    }
                }
                editor()
            }
        }
        EscapeButton("사건으로 돌아가기", "notebook-back", onBack, !busy, secondary = true)
    }
    clearId?.let { checkpointId ->
        AlertDialog(
            onDismissRequest = { clearId = null },
            title = { Text("현재 페이지의 메모를 지울까요?") },
            text = { Text("이 페이지의 메모만 지웁니다. 퍼즐 진행과 다른 페이지, 평가 기록은 그대로 보존됩니다.") },
            confirmButton = { TextButton(onClick = {
                clearId = null
                busy = true
                scope.launch {
                    val success = notebook.saveNote(session.sessionId, checkpointId, "")
                    if (success) { drafts[checkpointId] = ""; saved[checkpointId] = "" }
                    message = if (success) "현재 페이지를 비웠습니다." else "메모를 지우지 못했습니다. 다시 시도해 주세요."
                    busy = false
                }
            }, modifier = Modifier.heightIn(min = 56.dp).testTag("notebook-clear-confirm")) { Text("메모 지우기") } },
            dismissButton = { TextButton(onClick = { clearId = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text("취소") } },
        )
    }
}

@Composable
fun CaseCertificateScreen(state: GameUiState, onHome: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable(stateSaver = CertificateExportSaver) { mutableStateOf<CertificateExport?>(null) }
    var writing by remember { mutableStateOf(false) }
    val busy = pending != null || writing
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    // The launcher stays registered while the system file picker temporarily suspends this screen.
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val export = pending
        pending = null
        if (uri == null || export == null) {
            writing = false
            message = "이미지 저장을 취소했습니다."
        } else {
            writing = true
            scope.launch {
                message = try {
                    withContext(Dispatchers.IO) {
                        val typeface = context.resources.getFont(R.font.nanum_myeongjo)
                        val bitmap = createCertificateBitmap(export, typeface, context.resources)
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("PNG encoding failed")
                            } ?: throw IOException("Cannot open selected document")
                        } finally { bitmap.recycle() }
                    }
                    "선택한 위치에 기념카드 PNG를 저장했습니다."
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) { "이미지를 저장하지 못했습니다. 저장 위치를 다시 선택해 주세요." }
                writing = false
            }
        }
    }
    if (!state.canReadContent) { CasePausedScreen(state); return }
    val session = state.session
    val content = state.content
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-certificate"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CaseHeading("Certificate of Completion", "완주 기념카드")
        if (session == null || content == null || !isComplete(content, session)) {
            CasePanel { Text("모든 경유지를 처리하면 현재 게임의 기념카드가 열립니다.", color = CaseColors.Cream) }
            EscapeButton(stringResource(R.string.nav_lobby_return), "certificate-home", onHome)
            return@Column
        }
        val export = CertificateExport(
            title = content.title,
            sessionId = session.sessionId,
            solved = session.progress.count { it.status == CheckpointStatus.SOLVED },
            skipped = session.progress.count { it.status == CheckpointStatus.SKIPPED },
            hints = session.progress.sumOf { it.revealedHintCount },
            answers = session.progress.count { it.answerRevealed },
            date = LocalDate.now(ZoneId.of("Asia/Seoul")).toString(),
            simulation = session.sourceMode == TourMode.MOCK,
        )
        PaperSheet {
            Text("Escape Road", color = CaseColors.Burgundy, fontFamily = CaseFonts.Serif, fontSize = 38.sp)
            Text("CERTIFICATE OF COMPLETION", color = CaseColors.Ink, fontFamily = CaseFonts.Serif, letterSpacing = 3.sp)
            CaseArtwork(CaseArt.ENVELOPE, Modifier.fillMaxWidth().height(160.dp))
            Text("정차 후 이어가는 여행의 단서", color = CaseColors.Burgundy, style = MaterialTheme.typography.headlineMedium, fontFamily = CaseFonts.Serif)
            Text(export.title, color = CaseColors.Ink, style = MaterialTheme.typography.titleLarge)
            Text("해결 ${export.solved} · 건너뛰기 ${export.skipped} · 힌트 ${export.hints} · 정답 공개 ${export.answers}", color = CaseColors.Ink)
            Text("발급일 (KST) ${export.date} · 세션 ${export.sessionId.take(8)}", color = CaseColors.Ink, style = MaterialTheme.typography.bodySmall)
            Text(if(export.simulation)"개발용 시뮬레이션의 완료 기록입니다."else"실제 SDK 입력 모드의 앱 기록 · 실차 방문 인증과 구분합니다.", color = CaseColors.Burgundy)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EscapeButton(if (busy) "이미지 저장 중" else "이미지로 저장", "certificate-export", {
                pending = export
                message = null
                try { createDocument.launch("EscapeRoad-${session.sessionId.take(8)}.png") }
                catch (_: Exception) { writing = false; pending = null; message = "파일 선택기를 열지 못했습니다." }
            }, !busy)
            EscapeButton(stringResource(R.string.nav_lobby_return), "certificate-home", onHome, !busy, secondary = true)
        }
        Text("사진 촬영 없이 만든 기념카드입니다. 저장 버튼에서 PNG 파일의 위치를 직접 선택하세요.", color = CaseColors.MutedGold)
        message?.let { Text(it, color = CaseColors.Cream, modifier = Modifier.testTag("certificate-status")) }
    }
}

@Composable
fun CaseRatingScreen(state: GameUiState, onDone: () -> Unit) {
    if (!state.canReadContent) { CasePausedScreen(state); return }
    val session = state.session ?: return
    val context = LocalContext.current
    val notes = remember(context) { LocalCaseNotes(context) }
    val original = remember(session.sessionId) { notes.readRating(session.sessionId) }
    var satisfaction by rememberSaveable(session.sessionId) { mutableIntStateOf(original.satisfaction) }
    var difficulty by rememberSaveable(session.sessionId) { mutableIntStateOf(original.difficulty) }
    var atmosphere by rememberSaveable(session.sessionId) { mutableIntStateOf(original.atmosphere) }
    var review by rememberSaveable(session.sessionId) { mutableStateOf(original.review) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("screen-rating"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CaseHeading("Leave Your Mark", "테마 평가")
        CasePanel {
            Text(state.content?.title.orEmpty(), color = CaseColors.Gold, fontFamily = CaseFonts.Serif, style = MaterialTheme.typography.titleLarge)
            RatingChoices("만족도", "satisfaction", satisfaction, !busy) { satisfaction = it }
            RatingChoices("체감 난이도", "difficulty", difficulty, !busy) { difficulty = it }
            RatingChoices("분위기", "atmosphere", atmosphere, !busy) { atmosphere = it }
        }
        PaperSheet {
            Text("한 줄 후기", color = CaseColors.Burgundy, fontFamily = CaseFonts.Serif, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(review, { if (it.length <= 400) review = it }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("rating-review"), minLines = 2, colors = paperTextFieldColors(), placeholder = { Text("다음에 기억하고 싶은 점을 남겨 주세요.") })
            Text("이 평가는 이 기기에만 저장됩니다. 서버로 전송하지 않습니다.", color = CaseColors.Ink)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EscapeButton("평가 저장", "rating-save", {
                busy = true
                scope.launch {
                    val success = notes.saveRating(session.sessionId, LocalRating(satisfaction, difficulty, atmosphere, review))
                    message = if (success) "이 기기에 평가를 저장했습니다." else "평가를 저장하지 못했습니다. 다시 시도해 주세요."
                    busy = false
                }
            }, !busy)
            EscapeButton("돌아가기", "rating-done", onDone, !busy, secondary = true)
        }
        message?.let { Text(it, color = CaseColors.Cream, modifier = Modifier.testTag("rating-status")) }
    }
}

@Composable
fun LockCollectionScreen(state: GameUiState, onBack: () -> Unit) {
    if (!state.canReadContent) { CasePausedScreen(state); return }
    val devices = listOf(
        Triple("Combination Lock", "숫자 자물쇠", "화면 키패드로 정해진 자리 수를 입력합니다. 선행 0도 한 자리이며, 제출 버튼을 눌러 확인합니다."),
        Triple("Directional Lock", "순서 자물쇠", "위·오른쪽·아래·왼쪽 버튼으로 순서를 기록합니다. 마지막 입력을 지우거나 전체 순서를 다시 만들 수 있습니다."),
        Triple("State Combination", "상태 조합 자물쇠", "가상 장치의 채널과 레벨을 함께 맞춥니다. 실제 차량의 장치나 센서를 조작하지 않습니다."),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-locks"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CaseHeading("The Locksmith's Collection", "자물쇠 도감")
        Text("이번 테스트 게임에서 만날 세 가지 장치", color = CaseColors.MutedGold)
        devices.forEachIndexed { index, (english, korean, description) ->
            PaperSheet {
                Text("0${index + 1}  ·  $english", color = CaseColors.Burgundy, fontFamily = CaseFonts.Serif)
                Text(korean, color = CaseColors.Ink, style = MaterialTheme.typography.headlineSmall)
                CaseArtwork(if (index == 1) CaseArt.COMPASS else CaseArt.LOCK, Modifier.size(72.dp))
                Text(description, color = CaseColors.Ink)
                Text("테스트 게임에서 사용 가능", color = CaseColors.Burgundy)
            }
        }
        listOf("Word Lock · 알파벳 자물쇠", "Colour Lock · 색상 자물쇠", "Key Lock · 열쇠 자물쇠").forEach { title ->
            CasePanel {
                Text(title, color = CaseColors.MutedGold, fontFamily = CaseFonts.Serif, style = MaterialTheme.typography.titleLarge)
                Text("준비 중 · 이번 버전에서는 플레이할 수 없습니다.", color = CaseColors.MutedGold)
            }
        }
        EscapeButton("사건으로 돌아가기", "locks-back", onBack, secondary = true)
    }
}

@Composable
private fun RatingChoices(label: String, tag: String, value: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label  $value / 5", color = CaseColors.Cream, fontFamily = CaseFonts.Serif, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { number -> EscapeButton(if (number <= value) "◆ $number" else "◇ $number", "rating-$tag-$number", { onSelect(number) }, enabled, secondary = number != value) }
        }
    }
}

@Composable
private fun paperTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CaseColors.Ink, unfocusedTextColor = CaseColors.Ink,
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = CaseColors.Burgundy, unfocusedBorderColor = CaseColors.Gold,
    cursorColor = CaseColors.Burgundy, focusedPlaceholderColor = CaseColors.Ink.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = CaseColors.Ink.copy(alpha = 0.6f),
)

private fun isComplete(content: GamePackage, session: GameSession) =
    content.checkpoints.isNotEmpty() && session.progress.size == content.checkpoints.size && session.progress.all { it.status.isComplete }

private fun extraAnswerText(lock: LockDefinition): String = when (lock) {
    is LockDefinition.Numeric -> lock.answer
    is LockDefinition.Sequence -> lock.answer.joinToString(" → ") { when (it) { Direction.UP -> "위"; Direction.RIGHT -> "오른쪽"; Direction.DOWN -> "아래"; Direction.LEFT -> "왼쪽" } }
    is LockDefinition.StateCombination -> "채널 ${lock.channel} · 레벨 ${lock.level}"
}

private data class LocalRating(val satisfaction: Int = 3, val difficulty: Int = 3, val atmosphere: Int = 3, val review: String = "")

/** UI-only personal notes. This file never mutates the game's DataStore snapshot. */
private class LocalCaseNotes(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("roadescape-ui", Context.MODE_PRIVATE)
    fun readNote(session: String, checkpoint: String): String = preferences.getString("notebook.$session.$checkpoint", "").orEmpty()
    suspend fun saveNote(session: String, checkpoint: String, text: String): Boolean = withContext(Dispatchers.IO) {
        preferences.edit().putString("notebook.$session.$checkpoint", text).commit()
    }
    fun readRating(session: String): LocalRating = runCatching {
        val json = JSONObject(preferences.getString("rating.$session", "{}").orEmpty())
        LocalRating(json.optInt("satisfaction", 3).coerceIn(1, 5), json.optInt("difficulty", 3).coerceIn(1, 5), json.optInt("atmosphere", 3).coerceIn(1, 5), json.optString("review", "").take(400))
    }.getOrDefault(LocalRating())
    suspend fun saveRating(session: String, rating: LocalRating): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().put("satisfaction", rating.satisfaction).put("difficulty", rating.difficulty).put("atmosphere", rating.atmosphere).put("review", rating.review)
        preferences.edit().putString("rating.$session", json.toString()).commit()
    }
}

private data class CertificateExport(val title: String, val sessionId: String, val solved: Int, val skipped: Int, val hints: Int, val answers: Int, val date: String, val simulation:Boolean=true)

private val CertificateExportSaver = listSaver<CertificateExport?, Any>(
    save = { export ->
        if (export == null) emptyList() else listOf(
            export.title, export.sessionId, export.solved, export.skipped,
            export.hints, export.answers, export.date, export.simulation,
        )
    },
    restore = { values ->
        if (values.size !in 7..8) null else CertificateExport(
            values[0] as String, values[1] as String, values[2] as Int,
            values[3] as Int, values[4] as Int, values[5] as Int, values[6] as String, values.getOrNull(7) as? Boolean ?: true,
        )
    },
)

/** PDF artwork with live completion data, rather than the PDF's static sample results. */
private fun createCertificateBitmap(export: CertificateExport, typeface: Typeface, resources: Resources): Bitmap {
    val bitmap = Bitmap.createBitmap(1600, 1100, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val paper = BitmapFactory.decodeResource(resources, R.drawable.pdf_paper)
    try { canvas.drawBitmap(paper, null, RectF(0f, 0f, 1600f, 1100f), paint) }
    finally { paper.recycle() }
    paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = CaseColors.Gold.toArgb()
    canvas.drawRect(45f, 45f, 1555f, 1055f, paint); canvas.drawRect(57f, 57f, 1543f, 1043f, paint)
    paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
    fun text(value: String, y: Float, size: Float, color: Color = CaseColors.Ink, bold: Boolean = false) {
        paint.color = color.toArgb(); paint.textSize = size; paint.typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        canvas.drawText(value, 800f, y, paint)
    }
    text("Escape Road", 170f, 88f, CaseColors.Burgundy, true)
    text("CERTIFICATE OF COMPLETION", 230f, 34f)
    val envelope = BitmapFactory.decodeResource(resources, R.drawable.pdf_envelope)
    val scale = minOf(620f / envelope.width, 290f / envelope.height)
    val width = envelope.width * scale; val height = envelope.height * scale
    try { canvas.drawBitmap(envelope, null, RectF(800f-width/2, 415f-height/2, 800f+width/2, 415f+height/2), paint) }
    finally { envelope.recycle() }
    text("정차 후 이어가는 여행의 단서", 655f, 53f, CaseColors.Burgundy, true)
    val title = if (export.title.length <= 28) export.title else export.title.take(27) + "…"
    text(title, 730f, 39f)
    text("해결 ${export.solved} · 건너뛰기 ${export.skipped} · 힌트 ${export.hints} · 정답 공개 ${export.answers}", 810f, 35f)
    text("발급일 (KST) ${export.date}  |  세션 ${export.sessionId.take(8)}", 900f, 29f)
    text(if(export.simulation)"개발용 시뮬레이션 완료 기록 · 이 기기에서 생성"else"실제 SDK 모드 앱 기록 · 실차 방문 인증 아님", 966f, 28f, CaseColors.Burgundy)
    return bitmap
}
