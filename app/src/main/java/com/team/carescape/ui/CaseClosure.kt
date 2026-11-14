package com.team.carescape.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.EndingKind
import com.team.carescape.game.StoryLockDefinition
import com.team.carescape.game.StoryPuzzleInput
import com.team.carescape.game.StoryState
import com.team.carescape.game.TourCatalog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ───────── 진상 추리 다시 풀기 ─────────

/**
 * 일반 엔딩 뒤 한 번만 진상 추리를 다시 푼다. 각성도·오답 수는 바뀌지 않는다.
 * 사건 기록부를 다시 읽고 와도 고른 답은 이 화면이 열려 있는 동안 남는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TruthRetryPanel(s: StoryState, vm: GameViewModel, onClose: () -> Unit) {
    GoldFrame(padTop = 26.dp, padH = 40.dp, padBottom = 24.dp) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("truth-retry"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("One More Look", style = Esc.pinyon(36))
            Text("진상 다시 추리하기", style = Esc.nanum(32))
            Text("사건 기록부의 문서를 다시 떠올려 보세요. 단 한 번만 다시 풀 수 있고, 결과는 바꿀 수 없어요. 시귀 각성도는 바뀌지 않아요.",
                style = Esc.noto(16, Esc.creamMuted, lineHeight = 1.6f))
            s.truthSteps.forEachIndexed { n, step ->
                val lock = step.lock as? StoryLockDefinition.Choice ?: return@forEachIndexed
                Column(Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${n + 1}. " + step.prompt.lines().last(), style = Esc.nanum(20))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        lock.options.forEach { o ->
                            val on = vm.truthDraft[step.id] == o.id
                            Box(
                                Modifier.border(if (on) 2.dp else 1.dp, if (on) Esc.gold else Esc.goldDim)
                                    .background(if (on) Esc.gold.copy(alpha = 0.18f) else Color.Transparent)
                                    .clickable(role = Role.RadioButton) { vm.truthDraft = vm.truthDraft + (step.id to o.id) }
                                    .testTag("truth-retry-${step.id}-${o.id}").padding(horizontal = 18.dp, vertical = 12.dp),
                            ) { Text(o.label, style = Esc.noto(17, if (on) Esc.cream else Esc.creamMuted)) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val ready = s.truthSteps.all { it.id in vm.truthDraft }
                ParchmentButton("이대로 제출 (1회)", {
                    if (ready) { vm.retryTruth(vm.truthDraft.mapValues { StoryPuzzleInput.Choice(it.value) }); onClose() }
                }, Modifier.testTag("truth-retry-submit"), enabled = ready && vm.parked && !vm.saving, minWidth = 240.dp)
                OutlineButton("사건 기록부 보기", vm::openCasebook, minHeight = 56.dp)
                OutlineButton("취소", onClose, minHeight = 56.dp)
            }
        }
    }
}

// ───────── 사건 종결 보고서(추억 카드) ─────────

private val dateFmt = DateTimeFormatter.ofPattern("yyyy. M. d.")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.of("Asia/Seoul"))

fun durationLabel(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
}

/** 보고서 한 장. [onSave]가 있으면 사진으로 저장 버튼을 보여 준다(저장 이미지에는 버튼이 들어가지 않는다). */
@Composable
fun CaseReport(s: StoryState, vm: GameViewModel, onClose: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saved by remember { mutableStateOf<String?>(null) }
    GoldFrame(padTop = 22.dp, padH = 34.dp, padBottom = 20.dp) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1f).drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
                ReportSheet(s, vm.storyStartedAtMs, vm.storyEndedAtMs ?: System.currentTimeMillis(), vm.storyTourStarts)
            }
            Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                Text("사건 종결 보고서", style = Esc.nanum(22))
                Text("이번 여행의 수사 기록이에요. 사진으로 저장해 두고 함께 간 사람과 나눠 보세요.", style = Esc.noto(14, Esc.creamMuted, lineHeight = 1.6f))
                ParchmentButton("사진으로 저장", {
                    scope.launch {
                        val bmp = layer.toImageBitmap().asAndroidBitmap()
                        saved = withContext(Dispatchers.IO) { savePicture(context, bmp, "escape-road-case-${System.currentTimeMillis()}") }
                    }
                }, Modifier.testTag("report-save"), enabled = vm.parked, minWidth = 200.dp)
                saved?.let { Text(it, Modifier.testTag("report-saved"), style = Esc.noto(13, Esc.gold, lineHeight = 1.5f)) }
                OutlineButton("닫기", onClose, Modifier.testTag("report-close"), minWidth = 200.dp)
            }
        }
    }
}

/** 보고서 종이 한 장(저장 이미지에 들어가는 부분). */
@Composable
fun ReportSheet(s: StoryState, start: Long, end: Long, tourStarts: Map<String, Long>) {
    val kind = s.endingKind
    val ending = kind?.let { s.episode.endings[it] }
    val stamp = if (kind == EndingKind.BAD) Esc.locked else Esc.seal
    Box(Modifier.fillMaxSize().parchment().padding(10.dp).border(1.dp, Esc.parchEdge).testTag("report-sheet")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Case Closed · Escape Road", style = Esc.cormorant(15, Esc.inkLabel))
                    Text("${s.episode.title}, ${s.episode.subtitle}", style = Esc.nanum(26, Esc.wine))
                }
                Text(if (local(start).toLocalDate() == local(end).toLocalDate()) local(start).format(dateFmt)
                    else "${local(start).format(dateFmt)} ~ ${local(end).format(dateFmt)}", style = Esc.noto(15, Esc.inkBody))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Esc.parchEdge))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportStat("수사 시간", durationLabel(end - start), Modifier.weight(1f))
                ReportStat("힌트", "${s.hintsUsed}회", Modifier.weight(1f))
                ReportStat("오답", "${s.puzzleProgress.sumOf { it.wrongAttempts }}회", Modifier.weight(1f))
                ReportStat("시귀 각성도", "${s.awakening.coerceAtMost(10)} / 10", Modifier.weight(1f))
            }
            Text("수사 경로", style = Esc.nanum(17, Esc.ink))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                s.episode.chapters.forEachIndexed { i, c ->
                    val at = tourStarts[TourCatalog.checkpoints.getOrNull(i)?.id]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(24.dp).background(Esc.seal, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = Esc.cormorant(14, Esc.parchment, 0f, FontWeight.Bold))
                        }
                        Text(c.place, Modifier.width(150.dp), style = Esc.nanum(16, Esc.ink))
                        Text(at?.let { "${local(it).format(timeFmt)} 관광 시작" } ?: "", Modifier.width(110.dp), style = Esc.noto(13, Esc.inkSoft))
                        Text(c.clue ?: "", Modifier.weight(1f), style = Esc.noto(13, Esc.inkBody))
                    }
                }
            }
            Text("모은 소지품 ${s.inventory.size}개 · 찾은 기록 ${s.visibleDocuments.size}장" + (if (s.truthRetryUsed) " · 진상 다시 추리 ${if (s.truthSolved) "성공" else "실패"}" else ""),
                style = Esc.noto(14, Esc.inkBody))
            if (ending != null) Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ending.script, style = Esc.pinyon(30, Esc.wine))
                // 엔딩 2026년 마무리의 마지막 문장을 한 줄 남긴다.
                val line = lastSentence(ending.epilogue)
                if (line.isNotBlank()) Text(line, Modifier.testTag("report-line"), style = Esc.nanum(18, Esc.inkBody, lineHeight = 1.5f))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(ending?.note ?: "", Modifier.align(Alignment.BottomStart).width(380.dp), style = Esc.noto(14, Esc.inkSoft, lineHeight = 1.5f))
                Column(
                    Modifier.align(Alignment.BottomEnd).rotate(-8f).border(2.dp, stamp).padding(2.dp).border(2.dp, stamp).padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (kind == EndingKind.BAD) "UNSEALED" else "SOLVED", style = Esc.cormorant(34, stamp, 0f, FontWeight.Bold))
                    Text(listOfNotNull(kind?.label, ending?.title).joinToString(" · "), Modifier.testTag("report-ending"), style = Esc.nanum(15, stamp), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ReportStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.border(1.dp, Esc.parchEdge).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Esc.noto(13, Esc.inkSoft))
        Text(value, style = Esc.nanum(18, Esc.ink))
    }
}

/** 사진첩(Pictures/EscapeRoad)에 저장한다. Android 10 이상에서만 권한 없이 저장할 수 있다. */
private fun savePicture(context: Context, bitmap: android.graphics.Bitmap, name: String): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "이 기기에서는 사진 저장을 지원하지 않아요."
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EscapeRoad")
        }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        "사진첩의 EscapeRoad 폴더에 저장했어요."
    }.getOrElse { "저장하지 못했어요. 다시 시도해 주세요." }
}

/** 문단의 마지막 문장(따옴표로 끝나는 문장 포함). */
fun lastSentence(text: String): String =
    text.trim().split(Regex("""(?<=[.!?…]["”']?)\s+""")).lastOrNull { it.isNotBlank() }?.trim().orEmpty()
