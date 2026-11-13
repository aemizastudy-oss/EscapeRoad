package com.example.roadescape.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.roadescape.R

/**
 * 「Escape Road 화면 디자인」 캔버스의 글꼴. 모두 SIL OFL 1.1이며 원문은 assets/licenses/.
 * Noto Serif KR은 앱 문구에 쓰는 글자만 남긴 부분집합이다(예약 이름 없음).
 */
@OptIn(ExperimentalTextApi::class)
object CaseFonts {
    /** 이전 화면 호환: 나눔명조 Regular. */
    val Serif = FontFamily(Font(R.font.nanum_myeongjo))
    /** 제목: 나눔명조 700/800. */
    val Heading = FontFamily(
        Font(R.font.nanum_myeongjo, FontWeight.Normal),
        Font(R.font.nanum_myeongjo_bold, FontWeight.Bold),
        Font(R.font.nanum_myeongjo_bold, FontWeight.SemiBold),
        Font(R.font.nanum_myeongjo_extrabold, FontWeight.ExtraBold),
    )
    /** 본문: Noto Serif KR 400/700. */
    val Body = FontFamily(
        Font(R.font.noto_serif_kr_regular, FontWeight.Normal),
        Font(R.font.noto_serif_kr_regular, FontWeight.Medium),
        Font(R.font.noto_serif_kr_bold, FontWeight.SemiBold),
        Font(R.font.noto_serif_kr_bold, FontWeight.Bold),
    )
    /** 영문 필기체 장식(Pinyon Script). 기울임 요청에도 같은 글꼴을 써서 가짜 기울임을 막는다. */
    val Script = FontFamily(
        Font(R.font.pinyon_script, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.pinyon_script, FontWeight.Normal, FontStyle.Italic),
    )
    /** 영문 소제목(Cormorant SC). */
    val SmallCaps = FontFamily(
        Font(R.font.cormorant_sc_medium, FontWeight.Medium),
        Font(R.font.cormorant_sc_medium, FontWeight.Normal),
        Font(R.font.cormorant_sc_bold, FontWeight.Bold),
    )
    val PlayfairSC = FontFamily(Font(R.font.playfair_display_sc_bold, FontWeight.Bold))
    val PlayfairItalic = FontFamily(
        Font(R.font.playfair_display_italic, FontWeight.Medium, FontStyle.Italic,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    )
}

/** 캔버스 hex 그대로. */
object CaseColors {
    val Burgundy = Color(0xFF4A1418)
    val DeepBurgundy = Color(0xFF2A070B)
    val Gold = Color(0xFFC9A15A)
    val Cream = Color(0xFFEFE4CC)
    val Ink = Color(0xFF2B1A16)
    val Red = Color(0xFF7A2026)
    val MutedGold = Color(0xFFDCCDB0)
    val Panel = Color(0x991E0508)       // rgba(30,5,8,0.6)
    val ButtonInk = Color(0xFF3E1014)
    val Label = Color(0xFFC9B896)
    val InkLabel = Color(0xFF6A4A30)
    val Wine = Color(0xFF5A1A1E)
    val ParchEdge = Color(0xFFA8875A)
}

/** 캔버스 소제목 스타일(Cormorant SC, 자간 0.2em). */
fun smallCaps(size: Int, color: Color = CaseColors.Gold, spacing: Float = 0.2f) =
    TextStyle(fontFamily = CaseFonts.SmallCaps, fontWeight = FontWeight.Medium, fontSize = size.sp, letterSpacing = spacing.em, color = color)

/** 캔버스 필기체 제목(Pinyon Script). */
fun script(size: Int, color: Color = CaseColors.Gold) =
    TextStyle(fontFamily = CaseFonts.Script, fontSize = size.sp, lineHeight = (size * 1.1f).sp, color = color)

private val EscapeColors = darkColorScheme(
    primary = CaseColors.Gold, onPrimary = CaseColors.Ink,
    secondary = CaseColors.Cream, onSecondary = CaseColors.Ink,
    background = CaseColors.Burgundy, onBackground = CaseColors.Cream,
    surface = CaseColors.DeepBurgundy, onSurface = CaseColors.Cream,
    surfaceVariant = Color(0xFF59252A), onSurfaceVariant = CaseColors.MutedGold,
    outline = CaseColors.Gold, error = Color(0xFFFFB4A8),
)

@Composable
fun RoadEscapeTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun heading(style: TextStyle, weight: FontWeight = FontWeight.ExtraBold) = style.copy(fontFamily = CaseFonts.Heading, fontWeight = weight)
    fun body(style: TextStyle) = style.copy(fontFamily = CaseFonts.Body)
    val type = Typography(
        displayLarge = heading(base.displayLarge), displayMedium = heading(base.displayMedium), displaySmall = heading(base.displaySmall),
        headlineLarge = heading(base.headlineLarge), headlineMedium = heading(base.headlineMedium), headlineSmall = heading(base.headlineSmall),
        titleLarge = heading(base.titleLarge, FontWeight.Bold), titleMedium = heading(base.titleMedium, FontWeight.Bold), titleSmall = heading(base.titleSmall, FontWeight.Bold),
        bodyLarge = body(base.bodyLarge).copy(lineHeight = 28.sp), bodyMedium = body(base.bodyMedium).copy(lineHeight = 24.sp), bodySmall = body(base.bodySmall),
        labelLarge = body(base.labelLarge), labelMedium = body(base.labelMedium), labelSmall = body(base.labelSmall),
    )
    MaterialTheme(colorScheme = EscapeColors, typography = type, shapes = Shapes(
        small = RoundedCornerShape(2.dp), medium = RoundedCornerShape(2.dp), large = RoundedCornerShape(3.dp),
    ), content = content)
}
