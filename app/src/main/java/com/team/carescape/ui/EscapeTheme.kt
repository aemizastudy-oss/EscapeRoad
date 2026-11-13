package com.team.carescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.team.carescape.R

/**
 * 「Escape Road 화면 디자인」 캔버스(claude.ai 디자인 아티팩트)의 색·글꼴·질감을 그대로 옮긴 토큰과 부품.
 * 화면은 시안과 같은 1280×720 좌표(1dp = 시안 1px)로 그린다. 확대는 [EscapeApp]이 맡는다.
 * 글꼴: 나눔명조(제목), Noto Serif KR(본문, 앱에 쓰는 글자만 남긴 부분집합), Pinyon Script, Cormorant SC, Playfair Display(SC).
 * 모두 SIL OFL 1.1 — 라이선스 원문은 assets/licenses/.
 */
@OptIn(ExperimentalTextApi::class)
object Esc {
    // ---- 색 (시안의 hex 그대로) ----
    val wall = Color(0xFF4A1418)
    val wallDeep = Color(0xFF2A070B)
    val panel = Color(0x991E0508)          // rgba(30,5,8,0.6)
    val gold = Color(0xFFC9A15A)
    val goldDim = Color(0x73C9A15A)        // rgba(201,161,90,0.45)
    val goldFaint = Color(0x4DC9A15A)      // rgba(201,161,90,0.3)
    val parchment = Color(0xFFEFE4CC)
    val parchEdge = Color(0xFFA8875A)
    val ink = Color(0xFF2B1A16)
    val inkBody = Color(0xFF3A2A20)
    val inkSoft = Color(0xFF5A3E2A)
    val inkLabel = Color(0xFF6A4A30)
    val btnInk = Color(0xFF3E1014)
    val oxblood = Color(0xFF7A2E2A)
    val seal = Color(0xFF7A2026)
    val sealRim = Color(0xFFA33A40)
    val wine = Color(0xFF5A1A1E)
    val cream = Color(0xFFEFE4CC)
    val creamSoft = Color(0xFFDCCDB0)
    val creamMuted = Color(0xFFE3D5BA)
    val label = Color(0xFFC9B896)
    val locked = Color(0xFF9C8C74)
    val alarm = Color(0xFFB83A33)

    // ---- 글꼴 ----
    val nanum = FontFamily(
        Font(R.font.nanum_myeongjo_bold, FontWeight.Bold),
        Font(R.font.nanum_myeongjo_extrabold, FontWeight.ExtraBold),
    )
    val noto = FontFamily(
        Font(R.font.noto_serif_kr_regular, FontWeight.Normal),
        Font(R.font.noto_serif_kr_regular, FontWeight.Medium),
        Font(R.font.noto_serif_kr_bold, FontWeight.Bold),
    )
    val pinyon = FontFamily(Font(R.font.pinyon_script))
    val cormorant = FontFamily(
        Font(R.font.cormorant_sc_medium, FontWeight.Medium),
        Font(R.font.cormorant_sc_bold, FontWeight.Bold),
    )
    val playfairSC = FontFamily(Font(R.font.playfair_display_sc_bold, FontWeight.Bold))
    val playfairItalic = FontFamily(
        Font(
            R.font.playfair_display_italic,
            FontWeight.Medium,
            FontStyle.Italic,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
    )

    fun pinyon(size: Int, color: Color = gold, lineHeight: Float = 1f) =
        TextStyle(fontFamily = pinyon, fontSize = size.sp, color = color, lineHeight = (size * lineHeight).sp)

    fun cormorant(size: Int, color: Color = gold, spacing: Float = 0.2f, weight: FontWeight = FontWeight.Medium) =
        TextStyle(fontFamily = cormorant, fontSize = size.sp, color = color, letterSpacing = spacing.em, fontWeight = weight)

    fun nanum(size: Int, color: Color = cream, weight: FontWeight = FontWeight.ExtraBold, lineHeight: Float = 1.3f) =
        TextStyle(fontFamily = nanum, fontSize = size.sp, color = color, fontWeight = weight, lineHeight = (size * lineHeight).sp)

    fun noto(size: Int, color: Color = cream, weight: FontWeight = FontWeight.Normal, lineHeight: Float = 1.5f) =
        TextStyle(fontFamily = noto, fontSize = size.sp, color = color, fontWeight = weight, lineHeight = (size * lineHeight).sp)

    // ---- 이전 화면(퍼즐형 테마) 호환용 ----
    val serif = noto
    val script = TextStyle(fontFamily = pinyon, color = gold)
    val smallCaps = TextStyle(fontFamily = cormorant, letterSpacing = 0.2.em, fontWeight = FontWeight.Medium)
    val title = TextStyle(fontFamily = nanum, fontWeight = FontWeight.ExtraBold, color = cream)
    val body = TextStyle(fontFamily = noto, color = cream, lineHeight = 28.sp, fontSize = 17.sp)
    val creamMutedLegacy = creamMuted
}

// ───────────────────────── 질감 ─────────────────────────

/** 시안의 다마스크 타일(120×168) 경로. 캔버스 업로드 SVG를 그대로 옮겼다. */
private val damaskPaths: List<Path> = listOf(
    "M60 14c6 10 16 14 16 26 0 9-7 14-12 12 5-4 4-11-1-13-4 9-3 18 3 26 5 7 15 9 21 4-1 9-11 14-20 10 2 8 0 16-7 22-7-6-9-14-7-22-9 4-19-1-20-10 6 5 16 3 21-4 6-8 7-17 3-26-5 2-6 9-1 13-5 2-12-3-12-12 0-12 10-16 16-26z",
    "M60 104l5 10-5 10-5-10z",
    "M0 84c4 6 10 9 10 16 0 6-4 9-8 8 3-3 2-7-1-8-1 0-1 0-1 0zM120 84c-4 6-10 9-10 16 0 6 4 9 8 8-3-3-2-7 1-8 1 0 1 0 1 0z",
    "M0 0l4 8-4 8zM120 0l-4 8 4 8zM0 152l4 8-4 8zM120 152l-4 8 4 8z",
).map { PathParser().parsePathString(it).toPath() }

private val damaskInk = Color(0x521A0306) // #1a0306, opacity 0.32

private fun DrawScope.drawDamask() {
    val tileW = 120.dp.toPx(); val tileH = 168.dp.toPx(); val s = 1.dp.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            translate(x, y) {
                scale(s, s, pivot = Offset.Zero) {
                    damaskPaths.forEach { drawPath(it, damaskInk) }
                    drawCircle(damaskInk, 3f, Offset(60f, 132f))
                }
            }
            x += tileW
        }
        y += tileH
    }
}

/** `.wall`: 다마스크 무늬 + 벨벳 사진(cover) + #4A1418. */
@Composable
fun Modifier.wall(): Modifier {
    val img = ImageBitmap.imageResource(R.drawable.bg_velvet)
    return this
        .background(Esc.wall)
        .drawBehind { drawCover(img); drawDamask() }
}

/** CSS `background-size: cover`처럼 이미지를 영역에 꽉 채우고 넘치는 부분은 잘라 그린다. 크기 측정에는 관여하지 않는다. */
private fun DrawScope.drawCover(img: ImageBitmap) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val scale = maxOf(w / img.width, h / img.height)
    val srcW = (w / scale).toInt().coerceIn(1, img.width)
    val srcH = (h / scale).toInt().coerceIn(1, img.height)
    drawImage(
        img,
        srcOffset = IntOffset((img.width - srcW) / 2, (img.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(w.toInt(), h.toInt()),
    )
}

/** `.parch`: 양피지 사진(cover) 위에 가장자리를 그을린 원형 그라데이션. [edge]는 가장자리 진하기(0.32 또는 parch2의 0.4). */
@Composable
fun Modifier.parchment(edge: Float = 0.32f, start: Float = 0.55f): Modifier {
    val img = ImageBitmap.imageResource(R.drawable.bg_parchment)
    return this
    .background(Esc.parchment)
    .drawBehind {
        drawCover(img)
        drawRect(
            Brush.radialGradient(
                start to Color.Transparent,
                1f to Color(110, 70, 30).copy(alpha = edge),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.maxDimension * 0.71f,
            ),
        )
    }
}

// ───────────────────────── 틀 ─────────────────────────

/**
 * 시안 공통 틀: 바깥 여백 22 · 금색 2px 테두리 · 8px 안쪽에 1px 선(outline-offset −8).
 * [padH]/[padTop]/[padBottom]은 시안의 padding 값(테두리 바깥 기준) 그대로 넣는다.
 */
@Composable
fun GoldFrame(
    padTop: Dp = 30.dp,
    padH: Dp = 40.dp,
    padBottom: Dp = 28.dp,
    fill: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(22.dp)
            .background(fill)
            .border(2.dp, Esc.gold)
            .padding(5.dp)
            .border(1.dp, Esc.gold)
            .padding(start = padH - 8.dp, end = padH - 8.dp, top = padTop - 8.dp, bottom = padBottom - 8.dp),
        content = content,
    )
}

// ───────────────────────── 버튼 ─────────────────────────

/** `.parch` 주 버튼: 금색 1px 테두리, 나눔명조 800, #3E1014. */
@Composable
fun ParchmentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = 220.dp,
    minHeight: Dp = 56.dp,
    fontSize: Int = 18,
) {
    Box(
        modifier
            .widthIn(min = minWidth)
            .heightIn(min = minHeight)
            .then(if (enabled) Modifier.parchment() else Modifier.background(Color(0x66EFE4CC)))
            .border(1.dp, Esc.gold, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Esc.nanum(fontSize, Esc.btnInk), textAlign = TextAlign.Center)
    }
}

/** 보조 버튼: rgba(30,5,8,0.6) 바탕, 금색 테두리, 나눔명조 700. [transparent]면 바탕 없음(로비). */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = 0.dp,
    minHeight: Dp = 56.dp,
    fontSize: Int = 17,
    transparent: Boolean = false,
) {
    Box(
        modifier
            .widthIn(min = minWidth)
            .heightIn(min = minHeight)
            .background(if (transparent) Color.Transparent else Esc.panel)
            .border(1.dp, if (enabled) Esc.gold else Esc.goldDim, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Esc.nanum(fontSize, if (enabled) Esc.cream else Esc.creamSoft.copy(alpha = 0.5f), FontWeight.Bold), textAlign = TextAlign.Center)
    }
}

// ───────────────────────── 작은 부품 ─────────────────────────

/** 금색 선 사이의 장식 문구. */
@Composable
fun Flourish(text: String, lineWidth: Dp = 70.dp, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(lineWidth).height(1.dp).background(Esc.gold))
        Text(text, style = style)
        Box(Modifier.width(lineWidth).height(1.dp).background(Esc.gold))
    }
}

/** "P · 주차 중" 배지. [boxed]는 단서 해금 화면의 금테 변형. */
@Composable
fun ParkBadge(label: String, boxed: Boolean = false) {
    Row(
        Modifier
            .background(if (boxed) Esc.panel else Color(0xBF1E0608))
            .border(1.dp, if (boxed) Esc.gold else Color(0xFF6E2A2E))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(if (boxed) 24.dp else 26.dp).border(1.dp, Esc.gold), contentAlignment = Alignment.Center) {
            Text("P", style = Esc.cormorant(if (boxed) 15 else 16, spacing = 0f, weight = FontWeight.Bold))
        }
        Text(label, style = Esc.noto(if (boxed) 15 else 14, if (boxed) Esc.cream else Esc.creamMuted, lineHeight = 1.2f))
    }
}

/** 시귀 각성도 막대(0~10). 퍼즐·힌트가 빠져 있어 지금은 늘 0이다. */
@Composable
fun AwakeningGauge(level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("시귀 각성도", style = Esc.noto(14, Esc.creamSoft, lineHeight = 1.2f))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(10) { i -> Box(Modifier.size(10.dp, 16.dp).background(if (i < level) Esc.alarm else Esc.goldFaint)) }
        }
        Text("$level / 10", style = Esc.cormorant(16, Esc.cream, spacing = 0f))
    }
}

/** 시안의 밀랍 봉인(물결 테두리). [glyph]가 있으면 가운데 글자(예: 水). */
@Composable
fun WaxSeal(modifier: Modifier, glyph: String? = null) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension / 74f
            val blob = PathParser().parsePathString(
                "M37 4 C48 2 56 10 62 16 C70 22 72 32 70 40 C72 50 64 60 56 64 C48 72 36 72 28 68 C18 68 8 60 6 50 C2 40 4 28 10 20 C16 10 26 4 37 4 Z",
            ).toPath()
            scale(s, s, pivot = Offset.Zero) {
                drawPath(blob, Esc.seal)
                drawCircle(Esc.sealRim, 22f, Offset(37f, 37f), style = Stroke(2f))
            }
        }
        if (glyph != null) Text(glyph, style = Esc.nanum(28, Color(0xFFC9585E), lineHeight = 1f))
    }
}

/** 봉인된 편지 아이콘(132×104). */
@Composable
fun Envelope(modifier: Modifier = Modifier.size(132.dp, 104.dp)) {
    Canvas(modifier) {
        val s = size.width / 132f
        scale(s, s, pivot = Offset.Zero) {
            drawRoundRect(Esc.parchment, Offset(4f, 10f), Size(124f, 86f), androidx.compose.ui.geometry.CornerRadius(3f))
            drawPath(Path().apply { moveTo(4f, 14f); lineTo(66f, 60f); lineTo(128f, 14f) }, Esc.parchEdge, style = Stroke(2f))
            drawCircle(Esc.seal, 20f, Offset(66f, 60f))
            drawCircle(Esc.sealRim, 13f, Offset(66f, 60f), style = Stroke(2f))
        }
    }
}

/** 로고 이미지. */
@Composable
fun Logo(width: Dp) {
    Image(painterResource(R.drawable.logo_escape_road), contentDescription = "Escape Road", modifier = Modifier.width(width), contentScale = ContentScale.FillWidth)
}

/** ◆◆◆◇◇ 같은 등급 표시. */
fun diamonds(filled: Int, total: Int = 5): String = "◆".repeat(filled) + "◇".repeat(total - filled)

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/** 받침에 맞춰 목적격 조사(을/를)를 붙인다. */
fun withObject(word: String): String {
    val last = word.lastOrNull() ?: return word
    if (last !in '가'..'힣') return "${word}을(를)"
    return if ((last - '가') % 28 != 0) "${word}을" else "${word}를"
}
