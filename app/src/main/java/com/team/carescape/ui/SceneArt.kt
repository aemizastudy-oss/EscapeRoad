package com.team.carescape.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import com.team.carescape.game.SceneBg
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 장면 그림. 한지 그림자극(종이 오리기) 느낌의 평면 실루엣으로, 모두 코드로 그린 원본 그림이다.
 * 배경은 1280×720, 초상은 300×420 설계 좌표로 그린 뒤 칸 크기에 맞춰 늘린다.
 * 나중에 일러스트 이미지가 생기면 이 두 함수만 이미지로 바꾸면 된다.
 */

private const val BW = 1280f
private const val BH = 720f
private const val PW = 300f
private const val PH = 420f

private val night = Color(0xFF0B0E1E)
private val ink = Color(0xFF120608)
private val goldLine = Color(0xFFC9A15A)

/**
 * 장소 배경. res/drawable-nodpi/scene_<이름 소문자>.png(또는 .jpg·.webp)가 있으면 그 그림을 칸에 꽉 채워 보여 주고,
 * 없으면 코드로 그린 실루엣 배경을 쓴다. 예: SceneBg.PALACE_DUSK → scene_palace_dusk
 */
@Composable
fun SceneBackdrop(bg: SceneBg, modifier: Modifier = Modifier) {
    val res = artResource("scene_" + bg.name.lowercase())
    if (res != 0) {
        Box(modifier) {
            Image(painterResource(res), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            // 대사 상자 쪽은 그림이 밝아도 글이 읽히게 어둡게 깐다.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC0A0204))))
        }
    } else DrawnBackdrop(bg, modifier)
}

/** 일러스트 파일 이름으로 drawable id를 찾는다. 없으면 0. */
@SuppressLint("DiscouragedApi")
@Composable
private fun artResource(name: String): Int {
    val context = LocalContext.current
    return remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
}

@Composable
private fun DrawnBackdrop(bg: SceneBg, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / BW, size.height / BH, pivot = Offset.Zero) {
            when (bg) {
                SceneBg.AGENCY -> agency()
                SceneBg.TIME_RIFT -> timeRift()
                SceneBg.PALACE_DUSK -> palace(dusk = true)
                SceneBg.PALACE_NIGHT -> palace(dusk = false)
                SceneBg.SLUICE_NIGHT -> sluice()
                SceneBg.RESERVOIR_DAWN -> reservoir()
                SceneBg.MUSEUM -> museum()
                SceneBg.LAKE_NIGHT -> lake()
            }
            // 아래쪽은 대사 상자가 놓이므로 어둡게 깐다.
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0204)), 430f, BH), Offset(0f, 430f), Size(BW, BH - 430f))
        }
    }
}

private fun DrawScope.sky(c0: Color, c1: Color, c2: Color, c3: Color? = null) =
    drawRect(Brush.verticalGradient(listOfNotNull(c0, c1, c2, c3), 0f, BH), size = Size(BW, BH))

private fun DrawScope.ripples(top: Float, color: Color, rows: Int = 6) {
    repeat(rows) { r ->
        val y = top + 26f + r * 34f
        var x = (r % 2) * 60f
        while (x < BW) {
            drawLine(color, Offset(x, y), Offset(x + 70f - r * 4f, y), strokeWidth = 2f)
            x += 170f
        }
    }
}

private fun DrawScope.lantern(x: Float, y: Float, glow: Boolean) {
    if (glow) drawCircle(Brush.radialGradient(listOf(Color(0x88F2A65A), Color.Transparent), Offset(x, y), 60f), 60f, Offset(x, y))
    drawLine(ink, Offset(x, y - 34f), Offset(x, y - 14f), strokeWidth = 2f)
    drawOval(Color(0xFFB23A2A), Offset(x - 12f, y - 16f), Size(24f, 32f))
    drawOval(Color(0x66FFD9A0), Offset(x - 6f, y - 10f), Size(12f, 20f))
}

/** 기와지붕 건물 한 채의 실루엣. */
private fun DrawScope.hall(cx: Float, base: Float, w: Float, h: Float, color: Color, rim: Color) {
    val half = w / 2
    val body = Path().apply { addRect(androidx.compose.ui.geometry.Rect(cx - half * 0.78f, base - h * 0.55f, cx + half * 0.78f, base)) }
    drawPath(body, color)
    val roof = Path().apply {
        moveTo(cx - half, base - h * 0.5f)
        quadraticBezierTo(cx - half * 0.86f, base - h * 0.62f, cx - half * 0.72f, base - h * 0.78f)
        lineTo(cx + half * 0.72f, base - h * 0.78f)
        quadraticBezierTo(cx + half * 0.86f, base - h * 0.62f, cx + half, base - h * 0.5f)
        quadraticBezierTo(cx, base - h * 0.6f, cx - half, base - h * 0.5f)
        close()
        moveTo(cx - half * 0.5f, base - h * 0.78f)
        quadraticBezierTo(cx - half * 0.42f, base - h * 0.92f, cx - half * 0.34f, base - h)
        lineTo(cx + half * 0.34f, base - h)
        quadraticBezierTo(cx + half * 0.42f, base - h * 0.92f, cx + half * 0.5f, base - h * 0.78f)
        close()
    }
    drawPath(roof, color)
    drawPath(roof, rim, style = Stroke(2f))
    var x = cx - half * 0.7f
    while (x <= cx + half * 0.7f) { drawLine(rim.copy(alpha = rim.alpha * 0.6f), Offset(x, base - h * 0.5f), Offset(x, base), strokeWidth = 3f); x += w * 0.12f }
}

private fun DrawScope.palace(dusk: Boolean) {
    if (dusk) sky(Color(0xFF3A0C18), Color(0xFF8A3A2A), Color(0xFFD98A4A), Color(0xFF3A1210))
    else sky(night, Color(0xFF1A1630), Color(0xFF2A1420))
    if (dusk) drawCircle(Color(0x99F6C27A), 70f, Offset(1010f, 300f))
    else {
        drawCircle(Color(0xFFE8D9B0), 46f, Offset(1040f, 120f))
        repeat(40) { i -> drawCircle(Color(0x88FFFFFF), 1.6f, Offset((i * 197f) % BW, (i * 89f) % 300f)) }
    }
    val far = if (dusk) Color(0xFF4A1A1A) else Color(0xFF16101E)
    hall(220f, 470f, 300f, 170f, far, goldLine.copy(alpha = 0.25f))
    hall(1080f, 480f, 320f, 180f, far, goldLine.copy(alpha = 0.25f))
    // 담장
    drawRect(ink.copy(alpha = 0.92f), Offset(0f, 470f), Size(BW, 60f))
    drawLine(goldLine.copy(alpha = 0.4f), Offset(0f, 470f), Offset(BW, 470f), strokeWidth = 2f)
    hall(640f, 520f, 660f, 300f, ink, goldLine.copy(alpha = 0.55f))
    // 현판
    drawRect(Color(0xFF2A1A10), Offset(590f, 300f), Size(100f, 34f))
    drawRect(goldLine.copy(alpha = 0.8f), Offset(590f, 300f), Size(100f, 34f), style = Stroke(2f))
    drawRect(Color(0xFF1A0A0A), Offset(0f, 520f), Size(BW, 200f))
    listOf(330f, 950f).forEach { lantern(it, 420f, glow = true) }
    // 담장 아래 그림자 하나(밤에만)
    if (!dusk) {
        drawOval(ink, Offset(140f, 470f), Size(26f, 60f)); drawCircle(ink, 12f, Offset(153f, 462f))
    }
}

private fun DrawScope.sluice() {
    sky(night, Color(0xFF1C2238), Color(0xFF10141F))
    drawCircle(Color(0xFFE8D9B0), 38f, Offset(220f, 110f))
    // 방화수류정
    hall(1110f, 250f, 230f, 130f, ink, goldLine.copy(alpha = 0.4f))
    // 다리와 일곱 홍예
    drawRect(Color(0xFF2B2622), Offset(0f, 250f), Size(BW, 260f))
    drawRect(Color(0xFF1C1814), Offset(0f, 230f), Size(BW, 26f))
    drawLine(goldLine.copy(alpha = 0.5f), Offset(0f, 230f), Offset(BW, 230f), strokeWidth = 2f)
    val archW = 150f
    val start = (BW - archW * 7 - 18f * 6) / 2
    repeat(7) { i ->
        val x = start + i * (archW + 18f)
        val arch = Path().apply {
            moveTo(x, 510f); lineTo(x, 380f)
            arcTo(androidx.compose.ui.geometry.Rect(x, 305f, x + archW, 455f), 180f, 180f, false)
            lineTo(x + archW, 510f); close()
        }
        drawPath(arch, if (i == 3) Color(0xFF1E3A55) else Color(0xFF0D1522))
        drawPath(arch, goldLine.copy(alpha = 0.35f), style = Stroke(2f))
        // 판문(가운데 빼고 내린 모습)
        if (i != 3) drawRect(Color(0xFF3A2A1A), Offset(x + 6f, 440f), Size(archW - 12f, 70f))
    }
    drawRect(Color(0xFF0E1A2A), Offset(0f, 510f), Size(BW, 210f))
    ripples(510f, Color(0x557FA6C8))
    // 횃불과 병사 그림자
    listOf(120f, 1160f).forEach { x ->
        drawCircle(Brush.radialGradient(listOf(Color(0xAAF0883A), Color.Transparent), Offset(x, 190f), 70f), 70f, Offset(x, 190f))
        drawLine(ink, Offset(x, 190f), Offset(x, 232f), strokeWidth = 4f)
    }
    listOf(300f, 340f, 900f).forEach { x -> drawOval(ink, Offset(x, 180f), Size(22f, 52f)); drawCircle(ink, 11f, Offset(x + 11f, 172f)) }
}

private fun DrawScope.reservoir() {
    sky(Color(0xFF2E3550), Color(0xFF8C7A8C), Color(0xFFE8B89A), Color(0xFFF2D6B0))
    drawCircle(Color(0x66FFF0D0), 90f, Offset(900f, 380f))
    val hills = Path().apply {
        moveTo(0f, 380f); quadraticBezierTo(200f, 300f, 420f, 360f); quadraticBezierTo(640f, 290f, 860f, 350f)
        quadraticBezierTo(1080f, 300f, BW, 360f); lineTo(BW, 420f); lineTo(0f, 420f); close()
    }
    drawPath(hills, Color(0xFF5A5468))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFD9B8A0), Color(0xFF6A6A80)), 410f, BH), Offset(0f, 410f), Size(BW, 310f))
    ripples(410f, Color(0x66FFFFFF), 7)
    // 둑과 영화정
    val bank = Path().apply { moveTo(0f, 470f); lineTo(520f, 430f); lineTo(560f, 470f); lineTo(0f, 520f); close() }
    drawPath(bank, Color(0xFF2A2420))
    hall(300f, 450f, 190f, 110f, ink, goldLine.copy(alpha = 0.35f))
    // 갈대
    repeat(26) { i ->
        val x = 820f + i * 17f
        drawLine(Color(0xFF2A2420), Offset(x, 720f), Offset(x + (i % 3) * 6f - 6f, 600f - (i % 5) * 14f), strokeWidth = 3f)
    }
    // 둑 위 사람들
    listOf(150f, 185f, 420f).forEach { x -> drawOval(ink, Offset(x, 455f), Size(16f, 38f)); drawCircle(ink, 8f, Offset(x + 8f, 450f)) }
}

private fun DrawScope.museum() {
    sky(Color(0xFF2A2420), Color(0xFF3E342C), Color(0xFF1E1814))
    drawRect(Color(0xFF15100C), Offset(0f, 560f), Size(BW, 160f))
    // 조명 원뿔
    listOf(260f, 640f, 1020f).forEach { x ->
        val cone = Path().apply { moveTo(x - 20f, 0f); lineTo(x + 20f, 0f); lineTo(x + 170f, 560f); lineTo(x - 170f, 560f); close() }
        drawPath(cone, Brush.verticalGradient(listOf(Color(0x40FFE6B0), Color(0x08FFE6B0)), 0f, 560f))
    }
    // 진열장
    listOf(140f, 520f, 900f).forEachIndexed { i, x ->
        drawRect(Color(0xFF231B14), Offset(x, 400f), Size(240f, 160f))
        drawRect(Color(0x33BFD8E0), Offset(x, 250f), Size(240f, 150f))
        drawRect(goldLine.copy(alpha = 0.5f), Offset(x, 250f), Size(240f, 310f), style = Stroke(2f))
        // 안의 문서
        drawRect(Color(0xFFD9C6A0), Offset(x + 50f, 300f + i * 6f), Size(140f, 90f))
        repeat(4) { r -> drawLine(Color(0x664A3020), Offset(x + 62f, 318f + i * 6f + r * 16f), Offset(x + 176f, 318f + i * 6f + r * 16f), strokeWidth = 2f) }
        if (i == 1) {
            // 찢긴 소식지 조각
            listOf(-16f, 8f, 30f).forEachIndexed { k, dx -> drawRect(Color(0xFFE8DCC0), Offset(x + 70f + dx * 2, 420f + k * 18f), Size(60f, 26f)) }
        }
    }
    drawLine(goldLine.copy(alpha = 0.3f), Offset(0f, 560f), Offset(BW, 560f), strokeWidth = 2f)
}

private fun DrawScope.lake() {
    sky(Color(0xFF050814), Color(0xFF0E1630), Color(0xFF1C1A34))
    repeat(60) { i -> drawCircle(Color(0x77FFFFFF), if (i % 7 == 0) 2.2f else 1.2f, Offset((i * 211f) % BW, (i * 61f) % 330f)) }
    drawCircle(Color(0xFFE6E0C8), 40f, Offset(1060f, 110f))
    // 도시 불빛
    var x = 0f
    var k = 0
    while (x < BW) {
        val w = 50f + (k * 37 % 60)
        val h = 70f + (k * 53 % 130)
        drawRect(Color(0xFF0C1020), Offset(x, 420f - h), Size(w, h))
        var wy = 430f - h
        while (wy < 410f) { if ((k + wy.toInt()) % 3 == 0) drawRect(Color(0xAAF2D08A), Offset(x + 8f, wy), Size(6f, 6f)); wy += 16f }
        x += w + 6f; k++
    }
    // 전망대
    drawRect(Color(0xFF0A0C18), Offset(880f, 210f), Size(16f, 210f))
    drawRect(Color(0xFF0A0C18), Offset(850f, 200f), Size(76f, 16f))
    drawRect(Color(0xFF0A0C18), Offset(856f, 270f), Size(64f, 10f))
    drawRect(Color(0xFF0B1428), Offset(0f, 420f), Size(BW, 300f))
    // 물 위 반사
    repeat(14) { r -> drawLine(Color(0x55F2D08A), Offset(80f + r * 90f, 440f + (r % 4) * 40f), Offset(120f + r * 90f, 440f + (r % 4) * 40f), strokeWidth = 3f) }
    drawRect(Color(0x33E6E0C8), Offset(1040f, 440f), Size(40f, 6f))
    ripples(420f, Color(0x333F6A9A), 7)
    // 물가의 그림자들
    drawRect(Color(0xFF05060C), Offset(0f, 600f), Size(380f, 120f))
    listOf(90f, 150f, 230f).forEachIndexed { i, sx ->
        drawOval(Color(0xFF020306), Offset(sx, 528f + i * 4f), Size(24f, 76f))
        drawCircle(Color(0xFF020306), 12f, Offset(sx + 12f, 520f + i * 4f))
    }
}

private fun DrawScope.agency() {
    sky(Color(0xFF071014), Color(0xFF0C1A20), Color(0xFF050A0C))
    // 벽 격자
    var gx = 0f
    while (gx < BW) { drawLine(Color(0x1A7FD0D0), Offset(gx, 0f), Offset(gx, 560f)); gx += 64f }
    var gy = 0f
    while (gy < 560f) { drawLine(Color(0x1A7FD0D0), Offset(0f, gy), Offset(BW, gy)); gy += 64f }
    // 큰 지도 화면
    drawRect(Color(0xFF0E2A30), Offset(330f, 70f), Size(620f, 360f))
    drawRect(Color(0x997FD0D0), Offset(330f, 70f), Size(620f, 360f), style = Stroke(2f))
    val river = Path().apply { moveTo(560f, 70f); cubicTo(600f, 180f, 520f, 260f, 640f, 430f) }
    drawPath(river, Color(0x667FD0D0), style = Stroke(4f))
    listOf(Offset(600f, 300f), Offset(615f, 250f), Offset(650f, 210f), Offset(700f, 170f), Offset(760f, 120f)).forEachIndexed { i, p ->
        drawCircle(Color(0x55FF4040), 22f - i * 2, p); drawCircle(Color(0xFFFF5A4A), 6f, p)
    }
    // 보조 화면
    listOf(90f, 1000f).forEach { x ->
        drawRect(Color(0xFF0E2026), Offset(x, 150f), Size(190f, 240f))
        drawRect(Color(0x667FD0D0), Offset(x, 150f), Size(190f, 240f), style = Stroke(2f))
        repeat(6) { r -> drawLine(Color(0x557FD0D0), Offset(x + 16f, 180f + r * 32f), Offset(x + 60f + (r * 47 % 110), 180f + r * 32f), strokeWidth = 4f) }
    }
    drawRect(Color(0xFF040708), Offset(0f, 560f), Size(BW, 160f))
    drawLine(Color(0x557FD0D0), Offset(0f, 560f), Offset(BW, 560f), strokeWidth = 2f)
}

private fun DrawScope.timeRift() {
    sky(Color(0xFF14040A), Color(0xFF3A0A14), Color(0xFF14040A))
    val c = Offset(640f, 330f)
    repeat(9) { i -> drawCircle(goldLine.copy(alpha = 0.08f + i * 0.05f), 40f + i * 38f, c, style = Stroke(2f + i * 0.4f)) }
    repeat(36) { i ->
        val a = i * 10.0 * PI / 180
        val r1 = 80f + (i % 3) * 20f
        val r2 = 380f
        drawLine(goldLine.copy(alpha = 0.18f), Offset(c.x + (cos(a) * r1).toFloat(), c.y + (sin(a) * r1).toFloat()),
            Offset(c.x + (cos(a) * r2).toFloat(), c.y + (sin(a) * r2).toFloat()), strokeWidth = 1.5f)
    }
    repeat(12) { i ->
        val a = (i * 30.0 - 90) * PI / 180
        drawRect(goldLine.copy(alpha = 0.7f), Offset(c.x + (cos(a) * 300).toFloat() - 4f, c.y + (sin(a) * 300).toFloat() - 12f), Size(8f, 24f))
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xCCF2D6A0), Color.Transparent), c, 140f), 140f, c)
}

// ───────────────────────── 초상 ─────────────────────────

/**
 * 인물 초상(가슴 위). res/drawable-nodpi/portrait_<인물 id>.png(배경 투명)가 있으면 그 일러스트를,
 * 없으면 코드로 그린 그림을 쓴다. 칸 안에서 비율을 지키고 아래쪽에 붙인다. 사양은 docs/art/portraits.md.
 */
@Composable
fun Portrait(id: String, modifier: Modifier = Modifier) {
    val res = artResource("portrait_" + id)
    if (res != 0) Image(painterResource(res), null, modifier, contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter)
    else DrawnPortrait(id, modifier)
}

@Composable
private fun DrawnPortrait(id: String, modifier: Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width / PW, size.height / PH)
        withTransform({
            translate((size.width - PW * s) / 2, size.height - PH * s)
            scale(s, s, pivot = Offset.Zero)
        }) {
            when (id) {
                "physician" -> physician()
                "yeoni" -> yeoni()
                "hancheol" -> hancheol()
                "park" -> park()
                "kim" -> kim()
                "curator" -> curator()
                else -> agencyEmblem()
            }
        }
    }
}

private val rim = goldLine.copy(alpha = 0.55f)

private fun DrawScope.shoulders(color: Color) {
    val p = Path().apply {
        moveTo(20f, PH); cubicTo(30f, 300f, 80f, 270f, 120f, 262f); lineTo(180f, 262f)
        cubicTo(220f, 270f, 270f, 300f, 280f, PH); close()
    }
    drawPath(p, color); drawPath(p, rim, style = Stroke(2f))
}

private fun DrawScope.vCollar(color: Color) {
    val p = Path().apply { moveTo(118f, 264f); lineTo(150f, 340f); lineTo(182f, 264f); lineTo(170f, 264f); lineTo(150f, 312f); lineTo(130f, 264f); close() }
    drawPath(p, color)
}

private fun DrawScope.head(skin: Color, cx: Float = 150f, cy: Float = 190f) {
    drawRect(skin, Offset(cx - 18f, cy + 40f), Size(36f, 40f))
    drawOval(skin, Offset(cx - 50f, cy - 62f), Size(100f, 124f))
    drawOval(rim, Offset(cx - 50f, cy - 62f), Size(100f, 124f), style = Stroke(2f))
}

private fun DrawScope.face(cx: Float = 150f, cy: Float = 190f, brow: Color = ink, sad: Boolean = false, stern: Boolean = false) {
    drawOval(ink, Offset(cx - 26f, cy - 4f), Size(12f, 7f))
    drawOval(ink, Offset(cx + 14f, cy - 4f), Size(12f, 7f))
    val by = cy - 16f
    if (stern) {
        drawLine(brow, Offset(cx - 32f, by - 4f), Offset(cx - 12f, by + 2f), strokeWidth = 4f)
        drawLine(brow, Offset(cx + 32f, by - 4f), Offset(cx + 12f, by + 2f), strokeWidth = 4f)
    } else if (sad) {
        drawLine(brow, Offset(cx - 32f, by + 2f), Offset(cx - 12f, by - 3f), strokeWidth = 3f)
        drawLine(brow, Offset(cx + 32f, by + 2f), Offset(cx + 12f, by - 3f), strokeWidth = 3f)
    } else {
        drawLine(brow, Offset(cx - 32f, by), Offset(cx - 12f, by - 2f), strokeWidth = 3f)
        drawLine(brow, Offset(cx + 32f, by), Offset(cx + 12f, by - 2f), strokeWidth = 3f)
    }
    drawLine(ink.copy(alpha = 0.5f), Offset(cx, cy + 6f), Offset(cx - 4f, cy + 22f), strokeWidth = 2f)
    drawLine(ink.copy(alpha = 0.8f), Offset(cx - 12f, cy + 36f), Offset(cx + 12f, cy + 36f), strokeWidth = 3f)
}

private fun DrawScope.physician() {
    shoulders(Color(0xFF23304A)); vCollar(Color(0xFFE8E0D0))
    drawLine(Color(0xFF1A2236), Offset(150f, 340f), Offset(150f, PH), strokeWidth = 3f)
    head(Color(0xFFD9B48C)); face()
    drawLine(ink, Offset(138f, 220f), Offset(162f, 220f), strokeWidth = 2f) // 콧수염
    // 갓
    drawRect(Color(0xF0101014), Offset(112f, 48f), Size(76f, 82f))
    drawOval(Color(0xF0101014), Offset(112f, 40f), Size(76f, 18f))
    drawOval(Color(0xD0101014), Offset(30f, 118f), Size(240f, 28f))
    drawOval(rim, Offset(30f, 118f), Size(240f, 28f), style = Stroke(2f))
    drawLine(Color(0xFF101014), Offset(102f, 140f), Offset(110f, 250f), strokeWidth = 2f)
    drawLine(Color(0xFF101014), Offset(198f, 140f), Offset(190f, 250f), strokeWidth = 2f)
}

private fun DrawScope.yeoni() {
    shoulders(Color(0xFFA9C3A0))
    vCollar(Color(0xFFF2EDE0))
    // 고름
    drawLine(Color(0xFFA32A2A), Offset(150f, 330f), Offset(128f, PH), strokeWidth = 8f)
    drawLine(Color(0xFFA32A2A), Offset(150f, 330f), Offset(170f, 400f), strokeWidth = 8f)
    head(Color(0xFFE4D4C6)); face(sad = true)
    // 쪽 진 머리
    val hair = Path().apply {
        moveTo(98f, 200f); cubicTo(90f, 110f, 120f, 120f, 150f, 120f); cubicTo(180f, 120f, 210f, 110f, 202f, 200f)
        cubicTo(196f, 160f, 180f, 140f, 150f, 140f); cubicTo(120f, 140f, 104f, 160f, 98f, 200f); close()
    }
    drawPath(hair, Color(0xFF16100E))
    drawLine(Color(0xFF3A2A24), Offset(150f, 122f), Offset(150f, 140f), strokeWidth = 2f)
    drawCircle(Color(0xFF16100E), 26f, Offset(214f, 214f))
    drawLine(Color(0xFFC9C0B0), Offset(186f, 214f), Offset(246f, 206f), strokeWidth = 4f) // 비녀
    drawRect(Color(0xFFA32A2A), Offset(206f, 236f), Size(14f, 44f)) // 댕기
    // 창백한 기운
    drawOval(Color(0x223F6A9A), Offset(100f, 128f), Size(100f, 124f))
}

private fun DrawScope.hancheol() {
    shoulders(Color(0xFF1F2A44))
    drawRect(Color(0xFF8A2A24), Offset(40f, 330f), Size(46f, 90f))
    drawRect(Color(0xFF8A2A24), Offset(214f, 330f), Size(46f, 90f))
    vCollar(Color(0xFF3A3A44))
    head(Color(0xFFC89A70)); face(stern = true)
    // 전립
    drawOval(Color(0xF0141414), Offset(96f, 70f), Size(108f, 90f))
    drawOval(Color(0xF0141414), Offset(40f, 122f), Size(220f, 30f))
    drawOval(rim, Offset(40f, 122f), Size(220f, 30f), style = Stroke(2f))
    drawCircle(Color(0xFFC0302A), 14f, Offset(150f, 66f)) // 상모
    repeat(5) { i -> drawLine(Color(0xFFC0302A), Offset(150f, 66f), Offset(130f + i * 10f, 100f), strokeWidth = 3f) }
}

private fun DrawScope.park() {
    shoulders(Color(0xFFCDBB9A)); vCollar(Color(0xFFE6DCC8))
    head(Color(0xFFB98058)); face()
    // 상투와 머리
    val hair = Path().apply { moveTo(100f, 170f); cubicTo(100f, 120f, 200f, 120f, 200f, 170f); lineTo(196f, 150f); cubicTo(180f, 130f, 120f, 130f, 104f, 150f); close() }
    drawPath(hair, Color(0xFF1A1410))
    drawOval(Color(0xFF1A1410), Offset(136f, 104f), Size(28f, 30f))
    // 머리띠 수건
    drawRect(Color(0xFFEDE6D6), Offset(98f, 150f), Size(104f, 16f))
    drawLine(Color(0xFFEDE6D6), Offset(200f, 158f), Offset(232f, 176f), strokeWidth = 6f)
}

private fun DrawScope.kim() {
    shoulders(Color(0xFFE2DACB)); vCollar(Color(0xFFF2EEE4))
    head(Color(0xFFC8A07C)); face(brow = Color(0xFFEDEDED))
    // 흰 수염
    val beard = Path().apply { moveTo(118f, 214f); quadraticBezierTo(150f, 300f, 182f, 214f); quadraticBezierTo(150f, 236f, 118f, 214f); close() }
    drawPath(beard, Color(0xFFEFEDE6))
    // 패랭이
    val hat = Path().apply { moveTo(112f, 132f); lineTo(124f, 78f); lineTo(176f, 78f); lineTo(188f, 132f); close() }
    drawPath(hat, Color(0xFFC9A866)); drawPath(hat, rim, style = Stroke(2f))
    drawOval(Color(0xFFB8964E), Offset(60f, 124f), Size(180f, 22f))
    repeat(5) { i -> drawLine(Color(0x664A3A20), Offset(128f + i * 11f, 82f), Offset(122f + i * 13f, 130f), strokeWidth = 1.5f) }
}

private fun DrawScope.curator() {
    shoulders(Color(0xFFB89F7E))
    val shirt = Path().apply { moveTo(120f, 264f); lineTo(150f, 360f); lineTo(180f, 264f); close() }
    drawPath(shirt, Color(0xFFF0EEE8))
    drawLine(Color(0xFF3A4A6A), Offset(122f, 268f), Offset(150f, 380f), strokeWidth = 3f) // 목걸이 줄
    drawRect(Color(0xFFF0EEE8), Offset(136f, 376f), Size(30f, 40f))
    drawRect(Color(0xFF3A4A6A), Offset(140f, 382f), Size(22f, 6f))
    head(Color(0xFFE0BC98)); face()
    // 단발
    val hair = Path().apply {
        moveTo(92f, 240f); cubicTo(80f, 120f, 120f, 118f, 150f, 118f); cubicTo(180f, 118f, 220f, 120f, 208f, 240f)
        lineTo(196f, 240f); cubicTo(200f, 170f, 180f, 150f, 150f, 150f); cubicTo(130f, 150f, 104f, 160f, 104f, 240f); close()
    }
    drawPath(hair, Color(0xFF3A2418))
    // 동그란 안경
    drawCircle(Color(0xFF2A2A2A), 17f, Offset(130f, 188f), style = Stroke(3f))
    drawCircle(Color(0xFF2A2A2A), 17f, Offset(170f, 188f), style = Stroke(3f))
    drawLine(Color(0xFF2A2A2A), Offset(147f, 188f), Offset(153f, 188f), strokeWidth = 3f)
    // 흰 장갑
    drawOval(Color(0xFFF6F4EE), Offset(40f, 370f), Size(54f, 40f))
}

private fun DrawScope.agencyEmblem() {
    val c = Offset(150f, 230f)
    drawCircle(Brush.radialGradient(listOf(Color(0x557FD0D0), Color.Transparent), c, 150f), 150f, c)
    listOf(120f, 96f, 70f).forEachIndexed { i, r -> drawCircle(Color(0xFF7FD0D0).copy(alpha = 0.35f + i * 0.2f), r, c, style = Stroke(3f)) }
    val glass = Path().apply {
        moveTo(c.x - 34f, c.y - 50f); lineTo(c.x + 34f, c.y - 50f); lineTo(c.x + 4f, c.y); lineTo(c.x + 34f, c.y + 50f)
        lineTo(c.x - 34f, c.y + 50f); lineTo(c.x - 4f, c.y); close()
    }
    drawPath(glass, Color(0xFF7FD0D0).copy(alpha = 0.25f)); drawPath(glass, Color(0xFF7FD0D0), style = Stroke(3f))
    repeat(12) { i ->
        val a = i * 30.0 * PI / 180
        drawLine(Color(0xFF7FD0D0), Offset(c.x + (cos(a) * 112).toFloat(), c.y + (sin(a) * 112).toFloat()),
            Offset(c.x + (cos(a) * 124).toFloat(), c.y + (sin(a) * 124).toFloat()), strokeWidth = 3f)
    }
}
