package com.team.carescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer

/*
 * 소지품 그림. 200×200 설계 좌표에 물건 모양 그대로 그린다(선장본 수첩, 밀랍 봉인 밀서, 나무 열쇠패 등).
 * 모두 코드로 그린 원본 그림이며, 쓴 물건은 흐리게 그린다.
 */

private const val U = 200f

private val paper = Color(0xFFEADCBE)
private val paperEdge = Color(0xFFB89E70)
private val inkC = Color(0xFF3A2A20)
private val sealRed = Color(0xFF9A2A26)
private val shadow = Color(0x55000000)

@Composable
fun ItemArt(id: String, modifier: Modifier = Modifier, used: Boolean = false) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val s = minOf(size.width, size.height) / U
        withTransform({
            translate((size.width - U * s) / 2, (size.height - U * s) / 2)
            scale(s, s, pivot = Offset.Zero)
        }) {
            when (id) {
                "secret_letter" -> letter(tm)
                "recipe_first" -> recipe(tm)
                "notebook" -> notebook(tm)
                "key_eul" -> keyTag(tm)
                "bojagi" -> bojagi()
                "strong_water" -> gourd()
                "grain" -> sack()
                "flint" -> flint()
                "news" -> news(tm)
                "licorice" -> licorice()
                "vaccine" -> vaccine()
                "bowl" -> bowl()
                "paper_scrap" -> paperScrap()
                "dial_box" -> dialBox()
                "chest" -> chest()
                "gate_wheel" -> gateWheel()
                "stele" -> stele(tm)
                "map_scroll" -> mapScroll()
                "beacon_box" -> beaconBox()
                "pot" -> pot()
                "sign" -> sign(tm)
                "bottles" -> bottles()
                "shadow_figure" -> shadowFigure()
                else -> drawCircle(Esc.gold, 40f, Offset(100f, 100f), style = Stroke(3f))
            }
            if (used) drawRect(Color(0x99140608), size = Size(U, U))
        }
    }
}

/** 설계 좌표 [px] 크기의 글자를 [center]에 그린다. */
private fun DrawScope.glyph(tm: TextMeasurer, text: String, center: Offset, px: Float, color: Color, bold: Boolean = true) {
    val style = TextStyle(color = color, fontSize = px.toSp(), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    // 바깥 배율과 무관하게 설계 단위로 맞추기 위해, 글자는 배율이 적용된 좌표계에서 px 그대로 잰다.
    val layout = tm.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

private fun DrawScope.dropShadow(rect: Rect, r: Float = 6f) =
    drawRoundRect(shadow, Offset(rect.left + 6f, rect.top + 8f), rect.size, CornerRadius(r))

/** 한 줄씩 붓글씨 획처럼 보이는 가로줄. */
private fun DrawScope.brushLines(x: Float, y: Float, w: Float, rows: Int, gap: Float, color: Color = inkC.copy(alpha = 0.55f)) {
    repeat(rows) { r ->
        val len = w * (0.55f + ((r * 37) % 40) / 100f)
        drawLine(color, Offset(x, y + r * gap), Offset(x + len, y + r * gap), strokeWidth = 3.5f)
    }
}

private fun DrawScope.letter(tm: TextMeasurer) {
    rotate(-8f, Offset(100f, 100f)) {
        val r = Rect(28f, 46f, 172f, 150f)
        dropShadow(r, 2f)
        drawRect(paper, r.topLeft, r.size)
        // 접힌 자국
        drawLine(paperEdge, Offset(r.left, 98f), Offset(r.right, 98f), strokeWidth = 2f)
        drawLine(paperEdge.copy(alpha = 0.6f), Offset(100f, r.top), Offset(100f, r.bottom), strokeWidth = 1.5f)
        drawRect(paperEdge, r.topLeft, r.size, style = Stroke(2f))
        // 끈
        drawLine(Color(0xFF6A4A2A), Offset(r.left, 108f), Offset(r.right, 92f), strokeWidth = 3f)
        // 밀랍 봉인
        drawCircle(Color(0xFF6E1A18), 25f, Offset(102f, 100f))
        drawCircle(sealRed, 21f, Offset(100f, 98f))
        drawCircle(Color(0xFFC0504A), 21f, Offset(100f, 98f), style = Stroke(2f))
        glyph(tm, "密", Offset(100f, 98f), 22f, Color(0xFFF2D0C0))
    }
}

private fun DrawScope.recipe(tm: TextMeasurer) {
    rotate(5f, Offset(100f, 100f)) {
        val torn = Path().apply {
            moveTo(40f, 24f); lineTo(160f, 24f); lineTo(160f, 160f)
            listOf(150f to 170f, 138f to 158f, 126f to 172f, 112f to 160f, 98f to 174f, 84f to 160f, 70f to 172f, 56f to 158f, 40f to 168f)
                .forEach { (x, y) -> lineTo(x, y) }
            close()
        }
        drawPath(torn, shadow)
        withTransform({ translate(-5f, -7f) }) {
            drawPath(torn, paper); drawPath(torn, paperEdge, style = Stroke(2f))
            glyph(tm, "秘方", Offset(100f, 44f), 20f, inkC)
            brushLines(54f, 70f, 92f, 4, 14f)
            // 약초 그림
            drawLine(Color(0xFF4A6A3A), Offset(78f, 150f), Offset(100f, 124f), strokeWidth = 3f)
            drawOval(Color(0xFF6A8A4A), Offset(96f, 116f), Size(22f, 12f))
            drawOval(Color(0xFF6A8A4A), Offset(76f, 128f), Size(20f, 11f))
            drawCircle(sealRed.copy(alpha = 0.8f), 7f, Offset(140f, 146f))
        }
    }
}

private fun DrawScope.notebook(tm: TextMeasurer) {
    rotate(-6f, Offset(100f, 100f)) {
        val r = Rect(46f, 26f, 158f, 176f)
        dropShadow(r, 3f)
        // 속지 두께
        drawRect(Color(0xFFE6D6B4), Offset(r.left + 4f, r.top + 4f), Size(r.width, r.height))
        repeat(3) { i -> drawLine(paperEdge, Offset(r.right + 1f + i, r.top + 6f), Offset(r.right + 1f + i, r.bottom + 2f), strokeWidth = 1f) }
        // 표지
        drawRect(Brush.linearGradient(listOf(Color(0xFF3B2A4A), Color(0xFF2A1E36)), r.topLeft, Offset(r.right, r.bottom)), r.topLeft, r.size)
        drawRect(Color(0x55C9A15A), r.topLeft, r.size, style = Stroke(2f))
        // 오침안정법 실 매기(다섯 구멍)
        val spine = r.left + 14f
        drawLine(Color(0x55000000), Offset(spine + 6f, r.top), Offset(spine + 6f, r.bottom), strokeWidth = 3f)
        val holes = List(5) { r.top + 16f + it * (r.height - 32f) / 4 }
        holes.forEach { y ->
            drawLine(Color(0xFFB0302A), Offset(r.left, y), Offset(spine, y), strokeWidth = 3f)
            drawCircle(Color(0xFF1A1020), 2.5f, Offset(spine, y))
        }
        drawLine(Color(0xFFB0302A), Offset(r.left + 1f, holes.first()), Offset(r.left + 1f, holes.last()), strokeWidth = 3f)
        // 제첨(제목 쪽지)
        val label = Rect(r.right - 38f, r.top + 14f, r.right - 14f, r.top + 92f)
        drawRect(Color(0xFFE8DCC0), label.topLeft, label.size)
        drawRect(paperEdge, label.topLeft, label.size, style = Stroke(1.5f))
        glyph(tm, "手", Offset(label.center.x, label.top + 22f), 17f, inkC)
        glyph(tm, "帖", Offset(label.center.x, label.top + 54f), 17f, inkC)
        // 모서리 닳음과 물 얼룩
        drawCircle(Color(0x334A6A9A), 16f, Offset(r.left + 60f, r.bottom - 32f))
    }
}

private fun DrawScope.keyTag(tm: TextMeasurer) {
    // 붉은 술
    drawLine(Color(0xFFB0302A), Offset(100f, 20f), Offset(100f, 46f), strokeWidth = 3f)
    repeat(7) { i -> drawLine(Color(0xFFC0403A), Offset(100f, 20f), Offset(86f + i * 4.5f, 2f + (i % 2) * 4f), strokeWidth = 2f) }
    drawCircle(Color(0xFF8A2A24), 5f, Offset(100f, 20f))
    val r = Rect(64f, 44f, 136f, 184f)
    dropShadow(r, 12f)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFA9773F), Color(0xFF7A5228)), r.top, r.bottom), r.topLeft, r.size, CornerRadius(12f))
    // 나뭇결
    repeat(6) { i ->
        val x = r.left + 10f + i * 10f
        drawLine(Color(0x334A2A10), Offset(x, r.top + 8f), Offset(x + 4f * ((i % 3) - 1), r.bottom - 8f), strokeWidth = 1.5f)
    }
    drawRoundRect(Color(0x664A2A10), r.topLeft, r.size, CornerRadius(12f), style = Stroke(2f))
    drawCircle(Color(0xFF2A1A0A), 6f, Offset(100f, 58f))
    // 새긴 乙 (음각: 어두운 글자 + 밝은 테)
    glyph(tm, "乙", Offset(101f, 122f), 60f, Color(0x55FFE6B0))
    glyph(tm, "乙", Offset(100f, 120f), 60f, Color(0xFF3A2410))
}

private fun DrawScope.bojagi() {
    val c = Color(0xFF3F6F8F)
    val dark = Color(0xFF2C5068)
    val body = Path().apply {
        moveTo(100f, 70f); cubicTo(150f, 70f, 176f, 110f, 168f, 150f); cubicTo(160f, 182f, 40f, 182f, 32f, 150f)
        cubicTo(24f, 110f, 50f, 70f, 100f, 70f); close()
    }
    drawPath(body, shadow)
    withTransform({ translate(-4f, -6f) }) {
        drawPath(body, c)
        // 접힌 주름
        drawLine(dark, Offset(60f, 100f), Offset(90f, 160f), strokeWidth = 3f)
        drawLine(dark, Offset(140f, 100f), Offset(112f, 162f), strokeWidth = 3f)
        // 매듭과 귀
        val ear1 = Path().apply { moveTo(96f, 72f); quadraticBezierTo(60f, 40f, 70f, 22f); quadraticBezierTo(92f, 40f, 104f, 66f); close() }
        val ear2 = Path().apply { moveTo(104f, 72f); quadraticBezierTo(140f, 40f, 132f, 22f); quadraticBezierTo(108f, 40f, 96f, 66f); close() }
        drawPath(ear1, c); drawPath(ear2, c)
        drawPath(ear1, dark, style = Stroke(2f)); drawPath(ear2, dark, style = Stroke(2f))
        drawOval(dark, Offset(88f, 60f), Size(24f, 20f))
        // 조각보 바늘땀
        drawLine(Color(0x88EFE4CC), Offset(44f, 130f), Offset(156f, 130f), strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

private fun DrawScope.gourd() {
    val celadon = Color(0xFF8FB3A0)
    drawOval(shadow, Offset(52f, 160f), Size(100f, 22f))
    drawCircle(celadon, 50f, Offset(100f, 122f))
    drawCircle(celadon, 30f, Offset(100f, 66f))
    drawRect(celadon, Offset(88f, 80f), Size(24f, 16f))
    drawCircle(Color(0x66FFFFFF), 14f, Offset(80f, 104f))
    drawCircle(Color(0x55FFFFFF), 7f, Offset(88f, 56f))
    drawCircle(Color(0xFF5E8270), 50f, Offset(100f, 122f), style = Stroke(2f))
    // 마개와 끈
    drawRoundRect(Color(0xFF7A5228), Offset(90f, 26f), Size(20f, 16f), CornerRadius(4f))
    drawLine(Color(0xFFB0302A), Offset(86f, 92f), Offset(114f, 92f), strokeWidth = 4f)
    drawLine(Color(0xFFB0302A), Offset(114f, 92f), Offset(130f, 120f), strokeWidth = 3f)
    // 물결 문양(흐르는 물)
    repeat(3) { i ->
        val y = 120f + i * 12f
        val w = Path().apply { moveTo(66f, y); quadraticBezierTo(83f, y - 8f, 100f, y); quadraticBezierTo(117f, y + 8f, 134f, y) }
        drawPath(w, Color(0xFF3F6F8F), style = Stroke(2.5f))
    }
}

private fun DrawScope.sack() {
    val burlap = Color(0xFFB8955E)
    val body = Path().apply {
        moveTo(78f, 70f); cubicTo(40f, 90f, 30f, 170f, 60f, 178f); lineTo(140f, 178f)
        cubicTo(170f, 170f, 160f, 90f, 122f, 70f); close()
    }
    drawOval(shadow, Offset(40f, 168f), Size(130f, 20f))
    drawPath(body, burlap)
    // 올 무늬
    repeat(8) { i -> drawLine(Color(0x337A5228), Offset(56f + i * 12f, 86f), Offset(52f + i * 13f, 172f), strokeWidth = 1.2f) }
    repeat(6) { i -> drawLine(Color(0x337A5228), Offset(46f, 100f + i * 13f), Offset(154f, 100f + i * 13f), strokeWidth = 1.2f) }
    drawPath(body, Color(0xFF7A5A30), style = Stroke(2f))
    // 묶은 목
    drawPath(Path().apply { moveTo(80f, 72f); lineTo(86f, 44f); lineTo(114f, 44f); lineTo(120f, 72f); close() }, burlap)
    drawLine(Color(0xFF5A3A1A), Offset(78f, 66f), Offset(122f, 66f), strokeWidth = 5f)
    drawLine(Color(0xFF5A3A1A), Offset(120f, 66f), Offset(136f, 84f), strokeWidth = 3f)
    // 쏟아진 낟알
    val grain = Color(0xFFE6C77A)
    listOf(Offset(150f, 176f), Offset(160f, 170f), Offset(166f, 180f), Offset(144f, 184f), Offset(172f, 174f), Offset(156f, 186f)).forEachIndexed { i, p ->
        rotate(i * 40f, p) { drawOval(grain, Offset(p.x - 4f, p.y - 2.5f), Size(8f, 5f)) }
    }
    drawOval(grain, Offset(86f, 38f), Size(28f, 12f))
}

private fun DrawScope.flint() {
    drawOval(shadow, Offset(30f, 150f), Size(140f, 22f))
    val stone = Path().apply { moveTo(40f, 140f); lineTo(56f, 96f); lineTo(92f, 84f); lineTo(118f, 108f); lineTo(110f, 152f); lineTo(64f, 160f); close() }
    drawPath(stone, Color(0xFF8A8C90))
    drawPath(Path().apply { moveTo(56f, 96f); lineTo(92f, 84f); lineTo(84f, 118f); close() }, Color(0xFFA8AAAE))
    drawPath(stone, Color(0xFF55575C), style = Stroke(2f))
    // 부시(쇠)
    val striker = Path().apply {
        moveTo(118f, 70f); cubicTo(170f, 60f, 182f, 110f, 150f, 120f); lineTo(146f, 110f)
        cubicTo(168f, 102f, 160f, 74f, 122f, 82f); close()
    }
    drawPath(striker, Color(0xFF3A3A40))
    drawRoundRect(Color(0xFF7A5228), Offset(110f, 64f), Size(26f, 16f), CornerRadius(4f))
    // 불꽃
    val spark = Color(0xFFF2A03A)
    listOf(Offset(126f, 92f) to Offset(146f, 70f), Offset(126f, 92f) to Offset(150f, 92f), Offset(126f, 92f) to Offset(140f, 112f), Offset(126f, 92f) to Offset(118f, 66f))
        .forEach { (a, b) -> drawLine(spark, a, b, strokeWidth = 2.5f) }
    drawCircle(Color(0xFFFFE08A), 5f, Offset(126f, 92f))
}

private fun DrawScope.news(tm: TextMeasurer) {
    val r = Rect(26f, 36f, 174f, 168f)
    dropShadow(r, 1f)
    drawRect(Color(0xFFE6DAC0), r.topLeft, r.size)
    drawRect(paperEdge, r.topLeft, r.size, style = Stroke(2f))
    glyph(tm, "고을 소식", Offset(100f, 54f), 16f, inkC)
    drawLine(inkC.copy(alpha = 0.6f), Offset(36f, 66f), Offset(164f, 66f), strokeWidth = 2f)
    brushLines(38f, 82f, 120f, 6, 12f, inkC.copy(alpha = 0.45f))
    // 이어 붙인 찢긴 자국(조각 여섯)
    val seam = Color(0xFF9A7A4A)
    listOf(76f, 124f).forEach { x ->
        val p = Path().apply { moveTo(x, r.top); var y = r.top; var k = 0; while (y < r.bottom) { y += 8f; lineTo(x + if (k++ % 2 == 0) 4f else -4f, y) } }
        drawPath(p, seam, style = Stroke(1.5f))
    }
    val mid = Path().apply { moveTo(r.left, 112f); var x = r.left; var k = 0; while (x < r.right) { x += 8f; lineTo(x, 112f + if (k++ % 2 == 0) 4f else -4f) } }
    drawPath(mid, seam, style = Stroke(1.5f))
    // 테이프 조각과 뒷면 번호
    drawRect(Color(0x55FFF6D0), Offset(68f, 104f), Size(18f, 16f))
    drawRect(Color(0x55FFF6D0), Offset(116f, 104f), Size(18f, 16f))
    glyph(tm, "4172", Offset(150f, 156f), 12f, sealRed)
}

private fun DrawScope.licorice() {
    drawOval(shadow, Offset(36f, 152f), Size(130f, 22f))
    // 뿌리 다발
    val root = Color(0xFF9A6A3A)
    listOf(-14f, -6f, 2f, 10f).forEachIndexed { i, dy ->
        rotate(-18f + i * 4f, Offset(100f, 110f)) {
            drawRoundRect(root, Offset(44f, 100f + dy), Size(110f, 10f), CornerRadius(5f))
            drawLine(Color(0x553A2410), Offset(52f, 105f + dy), Offset(146f, 105f + dy), strokeWidth = 1f)
        }
    }
    drawLine(Color(0xFFB0302A), Offset(96f, 84f), Offset(104f, 132f), strokeWidth = 5f)
    // 썬 조각(노란 속, 방사 결)
    listOf(Offset(62f, 150f), Offset(92f, 158f), Offset(124f, 152f)).forEach { c ->
        drawOval(Color(0xFFE6C46A), Offset(c.x - 16f, c.y - 10f), Size(32f, 20f))
        drawOval(Color(0xFF8A5A2A), Offset(c.x - 16f, c.y - 10f), Size(32f, 20f), style = Stroke(2.5f))
        repeat(6) { k ->
            val a = k * 30f * (Math.PI / 180).toFloat()
            drawLine(Color(0x888A5A2A), c, Offset(c.x + 12f * kotlin.math.cos(a), c.y + 7f * kotlin.math.sin(a)), strokeWidth = 1f)
        }
    }
}

/** 깨진 사발(장면 소품). */
private fun DrawScope.bowl() {
    drawOval(shadow, Offset(30f, 132f), Size(150f, 30f))
    val body = Path().apply { moveTo(34f, 96f); quadraticBezierTo(100f, 190f, 166f, 96f); close() }
    drawPath(body, Color(0xFFD8CFBE)); drawPath(body, Color(0xFF8A7A60), style = Stroke(2.5f))
    drawOval(Color(0xFF6A5A48), Offset(34f, 86f), Size(132f, 22f))
    // 깨진 금과 검은 얼룩
    drawLine(Color(0xFF3A2A20), Offset(120f, 98f), Offset(106f, 128f), strokeWidth = 2.5f)
    drawLine(Color(0xFF3A2A20), Offset(106f, 128f), Offset(116f, 146f), strokeWidth = 2f)
    drawOval(Color(0xCC1A0A0A), Offset(70f, 90f), Size(46f, 12f))
    val shard = Path().apply { moveTo(150f, 150f); lineTo(176f, 140f); lineTo(170f, 160f); close() }
    drawPath(shard, Color(0xFFD8CFBE)); drawPath(shard, Color(0xFF8A7A60), style = Stroke(2f))
    // 기어간 자국
    listOf(Offset(24f, 170f), Offset(10f, 178f), Offset(-6f, 186f)).forEach { drawOval(Color(0x881A0A0A), Offset(it.x, it.y), Size(14f, 6f)) }
}

private val wood = Color(0xFF7A5228)
private val woodDark = Color(0xFF4A3018)
private val brass = Color(0xFFC9A15A)

/** 시각 바퀴 두 개가 달린 함(화홍문 수문지기). */
private fun DrawScope.dialBox() {
    val r = Rect(30f, 70f, 170f, 160f)
    dropShadow(r, 6f)
    drawRoundRect(wood, r.topLeft, r.size, CornerRadius(6f))
    drawRoundRect(woodDark, r.topLeft, r.size, CornerRadius(6f), style = Stroke(3f))
    drawLine(woodDark, Offset(r.left, 96f), Offset(r.right, 96f), strokeWidth = 3f)
    listOf(76f, 124f).forEach { x ->
        drawCircle(brass, 20f, Offset(x, 128f)); drawCircle(woodDark, 20f, Offset(x, 128f), style = Stroke(2f))
        repeat(12) { k ->
            val a = k * 30f * (Math.PI / 180).toFloat()
            drawLine(woodDark, Offset(x + 13f * kotlin.math.cos(a), 128f + 13f * kotlin.math.sin(a)),
                Offset(x + 18f * kotlin.math.cos(a), 128f + 18f * kotlin.math.sin(a)), strokeWidth = 1.5f)
        }
        drawLine(Color(0xFF8A2A24), Offset(x, 128f), Offset(x, 112f), strokeWidth = 3f)
    }
}

/** 놋쇠 자물쇠가 달린 나무 궤짝. */
private fun DrawScope.chest() {
    val r = Rect(28f, 76f, 172f, 164f)
    dropShadow(r, 4f)
    drawRect(wood, r.topLeft, r.size)
    drawRect(Color(0xFF8A6034), Offset(r.left, r.top), Size(r.width, 26f))
    listOf(48f, 152f).forEach { x -> drawRect(brass.copy(alpha = 0.8f), Offset(x - 6f, r.top), Size(12f, r.height)) }
    drawRect(woodDark, r.topLeft, r.size, style = Stroke(3f))
    // 자물쇠 판과 홈
    drawRoundRect(brass, Offset(84f, 100f), Size(32f, 40f), CornerRadius(4f))
    drawRect(woodDark, Offset(96f, 112f), Size(8f, 16f))
}

/** 수문 물레(만석거 둑 끝). */
private fun DrawScope.gateWheel() {
    drawRect(woodDark, Offset(40f, 120f), Size(120f, 50f))
    drawRect(Color(0xFF3F6F8F), Offset(48f, 150f), Size(104f, 20f))
    drawCircle(wood, 44f, Offset(100f, 92f), style = Stroke(9f))
    repeat(6) { k ->
        val a = k * 60f * (Math.PI / 180).toFloat()
        drawLine(wood, Offset(100f, 92f), Offset(100f + 44f * kotlin.math.cos(a), 92f + 44f * kotlin.math.sin(a)), strokeWidth = 6f)
    }
    drawCircle(woodDark, 10f, Offset(100f, 92f))
}

/** 깨진 비석(둑머리). */
private fun DrawScope.stele(tm: TextMeasurer) {
    drawOval(shadow, Offset(40f, 170f), Size(120f, 20f))
    val body = Path().apply {
        moveTo(56f, 180f); lineTo(56f, 54f); quadraticBezierTo(100f, 20f, 144f, 54f); lineTo(144f, 120f); lineTo(126f, 132f)
        lineTo(144f, 146f); lineTo(144f, 180f); close()
    }
    drawPath(body, Color(0xFF8C8A84)); drawPath(body, Color(0xFF55534E), style = Stroke(2.5f))
    glyph(tm, "萬", Offset(100f, 70f), 22f, Color(0xFF3A3834))
    glyph(tm, "石", Offset(100f, 100f), 22f, Color(0xFF3A3834))
    drawLine(Color(0xFF55534E), Offset(126f, 132f), Offset(92f, 150f), strokeWidth = 2f)
}

/** 말아 둔 고을 지도. */
private fun DrawScope.mapScroll() {
    val r = Rect(30f, 56f, 170f, 150f)
    dropShadow(r, 2f)
    drawRect(paper, r.topLeft, r.size)
    drawRect(paperEdge, r.topLeft, r.size, style = Stroke(2f))
    drawRoundRect(wood, Offset(22f, 48f), Size(12f, 110f), CornerRadius(5f))
    drawRoundRect(wood, Offset(166f, 48f), Size(12f, 110f), CornerRadius(5f))
    val river = Path().apply { moveTo(120f, 58f); cubicTo(90f, 90f, 130f, 110f, 80f, 148f) }
    drawPath(river, Color(0xFF3F6F8F), style = Stroke(4f))
    drawOval(Color(0xFF3F6F8F), Offset(96f, 112f), Size(26f, 14f))
    drawOval(Color(0xFF3F6F8F), Offset(70f, 132f), Size(30f, 14f))
    drawCircle(sealRed, 5f, Offset(60f, 80f))
}

/** 화구 다섯 개가 뚫린 봉화함. */
private fun DrawScope.beaconBox() {
    val r = Rect(24f, 82f, 176f, 160f)
    dropShadow(r, 4f)
    drawRect(Color(0xFF6A3A22), r.topLeft, r.size)
    drawRect(woodDark, r.topLeft, r.size, style = Stroke(3f))
    repeat(5) { i ->
        val x = 44f + i * 28f
        drawOval(Color(0xFF1A0A06), Offset(x - 10f, 96f), Size(20f, 12f))
        drawLine(Color(0x55F2A03A), Offset(x, 96f), Offset(x, 86f), strokeWidth = 2f)
    }
    drawRect(brass, Offset(88f, 128f), Size(24f, 18f))
}

/** 약탕기. */
private fun DrawScope.pot() {
    drawOval(shadow, Offset(40f, 164f), Size(120f, 20f))
    // 화로
    drawRect(Color(0xFF3A2A20), Offset(56f, 140f), Size(88f, 32f))
    drawOval(Color(0xAAF2A03A), Offset(70f, 136f), Size(60f, 12f))
    val body = Path().apply { moveTo(50f, 80f); quadraticBezierTo(100f, 160f, 150f, 80f); close() }
    drawPath(body, Color(0xFF8A5A36)); drawPath(body, Color(0xFF4A2A16), style = Stroke(2.5f))
    drawOval(Color(0xFF4A2A16), Offset(50f, 72f), Size(100f, 18f))
    drawLine(Color(0xFF4A2A16), Offset(150f, 86f), Offset(176f, 76f), strokeWidth = 6f)
    // 김
    repeat(3) { i ->
        val p = Path().apply { moveTo(80f + i * 20f, 66f); quadraticBezierTo(70f + i * 20f, 48f, 84f + i * 20f, 30f) }
        drawPath(p, Color(0x88FFFFFF), style = Stroke(2.5f))
    }
}

/** 호숫가 안내판. */
private fun DrawScope.sign(tm: TextMeasurer) {
    drawRect(woodDark, Offset(94f, 110f), Size(12f, 76f))
    val r = Rect(30f, 40f, 170f, 118f)
    dropShadow(r, 4f)
    drawRoundRect(Color(0xFF2E3A44), r.topLeft, r.size, CornerRadius(4f))
    drawRoundRect(Color(0xFF8AA0B0), r.topLeft, r.size, CornerRadius(4f), style = Stroke(2f))
    glyph(tm, "광교호수공원", Offset(100f, 58f), 13f, Color(0xFFE6EEF2))
    drawOval(Color(0xFF5A8AB0), Offset(50f, 74f), Size(46f, 30f))
    drawOval(Color(0xFF5A8AB0), Offset(104f, 82f), Size(52f, 26f))
    glyph(tm, "?", Offset(73f, 89f), 14f, Color.White)
    glyph(tm, "?", Offset(130f, 95f), 14f, Color.White)
}

/** 번호 붙은 약병 넷이 놓인 선반. */
private fun DrawScope.bottles() {
    drawRect(woodDark, Offset(20f, 150f), Size(160f, 12f))
    drawRect(woodDark, Offset(26f, 162f), Size(8f, 26f)); drawRect(woodDark, Offset(166f, 162f), Size(8f, 26f))
    listOf(Color(0xFFF4F1EA), Color(0xFFD8E4DC), Color(0xFFF4F1EA), Color(0xFFE6D8C8)).forEachIndexed { i, c ->
        val x = 42f + i * 38f
        val body = Path().apply {
            moveTo(x - 8f, 96f); lineTo(x + 8f, 96f); lineTo(x + 8f, 106f)
            cubicTo(x + 22f, 112f, x + 22f, 150f, x, 150f); cubicTo(x - 22f, 150f, x - 22f, 112f, x - 8f, 106f); close()
        }
        drawPath(body, c); drawPath(body, Color(0xFF8A8478), style = Stroke(1.5f))
        drawRoundRect(sealRed, Offset(x - 8f, 86f), Size(16f, 12f), CornerRadius(3f))
        drawRect(Color(0xFFEADCBE), Offset(x - 9f, 122f), Size(18f, 12f))
    }
}

/** 물가에 선 사람 모양의 그림자(호수 장면). 물 쪽으로는 다가오지 않는다. */
private fun DrawScope.shadowFigure() {
    drawOval(Color(0x55000000), Offset(50f, 184f), Size(100f, 14f))
    val body = Path().apply {
        moveTo(72f, 190f); cubicTo(66f, 140f, 70f, 96f, 84f, 78f); lineTo(116f, 78f)
        cubicTo(130f, 96f, 134f, 140f, 128f, 190f); close()
    }
    drawPath(body, Color(0xF0080608))
    drawOval(Color(0xF0080608), Offset(80f, 30f), Size(40f, 50f))
    // 흐트러진 머리카락과 희미한 눈빛
    drawLine(Color(0xF0080608), Offset(84f, 44f), Offset(70f, 96f), strokeWidth = 4f)
    drawLine(Color(0xF0080608), Offset(116f, 44f), Offset(128f, 100f), strokeWidth = 4f)
    drawCircle(Color(0x99C9D8E0), 2.2f, Offset(93f, 54f)); drawCircle(Color(0x99C9D8E0), 2.2f, Offset(107f, 54f))
}

/** 접힌 종이 쪽지(장면 소품). */
private fun DrawScope.paperScrap() {
    rotate(-12f, Offset(100f, 100f)) {
        val r = Rect(46f, 66f, 154f, 134f)
        dropShadow(r, 2f)
        drawRect(paper, r.topLeft, r.size)
        drawLine(paperEdge, Offset(100f, r.top), Offset(100f, r.bottom), strokeWidth = 2f)
        drawRect(paperEdge, r.topLeft, r.size, style = Stroke(2f))
        brushLines(56f, 82f, 36f, 3, 12f)
        brushLines(110f, 82f, 36f, 3, 12f)
    }
}

private fun DrawScope.vaccine() {
    // 은은한 빛
    drawCircle(Brush.radialGradient(listOf(Color(0x667FD0E0), Color.Transparent), Offset(100f, 110f), 90f), 90f, Offset(100f, 110f))
    drawOval(shadow, Offset(56f, 166f), Size(90f, 16f))
    val body = Path().apply {
        moveTo(86f, 62f); lineTo(114f, 62f); lineTo(114f, 80f)
        cubicTo(150f, 90f, 152f, 168f, 100f, 172f); cubicTo(48f, 168f, 50f, 90f, 86f, 80f); close()
    }
    drawPath(body, Color(0xFFF4F1EA))
    drawPath(body, Color(0xFFB8B4A8), style = Stroke(2f))
    // 청화 무늬
    drawLine(Color(0xFF3A5A9A), Offset(66f, 120f), Offset(134f, 120f), strokeWidth = 3f)
    val vine = Path().apply { moveTo(70f, 136f); quadraticBezierTo(85f, 124f, 100f, 136f); quadraticBezierTo(115f, 148f, 130f, 136f) }
    drawPath(vine, Color(0xFF3A5A9A), style = Stroke(2.5f))
    drawCircle(Color(0xFF3A5A9A), 4f, Offset(100f, 150f))
    drawCircle(Color(0x66FFFFFF), 8f, Offset(80f, 100f))
    // 붉은 천 마개
    drawRoundRect(Color(0xFFB0302A), Offset(82f, 44f), Size(36f, 22f), CornerRadius(8f))
    drawLine(Color(0xFF6A1A16), Offset(82f, 60f), Offset(118f, 60f), strokeWidth = 3f)
    drawLine(Color(0xFFB0302A), Offset(118f, 56f), Offset(130f, 74f), strokeWidth = 3f)
}
