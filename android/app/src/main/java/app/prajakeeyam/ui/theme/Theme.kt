package app.prajakeeyam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** iOS-style system palette: one blue accent, grey grouped background, semantic colours for states. */
object Ios {
    val Background = Color(0xFFF2F2F7)   // systemGroupedBackground
    val Card = Color(0xFFFFFFFF)
    val Label = Color(0xFF1C1C1E)
    val Secondary = Color(0xFF8E8E93)
    val Tertiary = Color(0xFFC7C7CC)
    val Separator = Color(0x4D3C3C43)
    val Fill = Color(0xFFE5E5EA)
    val FillSoft = Color(0xFFF2F2F7)
    val Blue = Color(0xFF007AFF)
    val Green = Color(0xFF34C759)
    val GreenDark = Color(0xFF1B7F3B)
    val Orange = Color(0xFFFF9500)
    val OrangeDark = Color(0xFFB45D00)
    val Red = Color(0xFFFF3B30)
    val Teal = Color(0xFF30B0C7)
    val Indigo = Color(0xFF5856D6)
    val Purple = Color(0xFFAF52DE)
    val Pink = Color(0xFFFF2D55)
    val Frost = Color(0xF2FFFFFF)        // translucent bars
}

private val LightColors = lightColorScheme(
    primary = Ios.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF003A75),
    secondary = Ios.Teal,
    onSecondary = Color.White,
    background = Ios.Background,
    onBackground = Ios.Label,
    surface = Ios.Card,
    onSurface = Ios.Label,
    surfaceVariant = Ios.Fill,
    onSurfaceVariant = Ios.Secondary,
    outline = Ios.Tertiary,
    error = Ios.Red,
)

// SF-like scale; roomy line heights so Telugu glyphs never clip.
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),      // large title
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),    // headline
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),                                        // body
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),                                       // subheadline
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),                                        // footnote
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PrajakeeyamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = AppTypography, content = content)
}
