package app.solco.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object SolcoColors {
    // Deep OLED true-dark surfaces so vibrant neon details pop cleanly
    val BgDeep = Color(0xFF05050A)
    val BgSurface = Color(0xFF0C0C14)
    val CardBg = Color(0xFF11111C)
    val CardElevated = Color(0xFF181828)
    val Border = Color(0xFF25253A)
    val BorderGlow = Color(0x669D7BFF)

    val TextPrimary = Color(0xFFF7F7FC)
    val TextSecondary = Color(0xFFA9A9C4)
    val TextMuted = Color(0xFF6B6B88)

    val AccentViolet = Color(0xFFA27BFF)
    val AccentCyan = Color(0xFF33E0FF)
    val AccentPink = Color(0xFFFF5590)
    val AccentMint = Color(0xFF28E0B9)
    val AccentAmber = Color(0xFFFFBE4F)
    val AccentCoral = Color(0xFFFF526F)
    val AccentGold = Color(0xFFFFD452)

    val PrimaryGradient = Brush.linearGradient(
        colors = listOf(AccentViolet, AccentPink)
    )

    val CyanVioletGradient = Brush.linearGradient(
        colors = listOf(AccentCyan, AccentViolet)
    )

    val MintCyanGradient = Brush.linearGradient(
        colors = listOf(AccentMint, AccentCyan)
    )
}

private val SolcoDarkScheme = darkColorScheme(
    primary = SolcoColors.AccentViolet,
    onPrimary = Color(0xFF06050D),
    secondary = SolcoColors.AccentCyan,
    onSecondary = Color(0xFF041016),
    tertiary = SolcoColors.AccentPink,
    onTertiary = Color.White,
    background = SolcoColors.BgDeep,
    onBackground = SolcoColors.TextPrimary,
    surface = SolcoColors.BgSurface,
    onSurface = SolcoColors.TextPrimary,
    surfaceVariant = SolcoColors.CardBg,
    onSurfaceVariant = SolcoColors.TextSecondary,
    outline = SolcoColors.Border,
    error = SolcoColors.AccentCoral
)

private val SolcoTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp,
        color = SolcoColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        color = SolcoColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
        color = SolcoColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = SolcoColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = SolcoColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = SolcoColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
        color = SolcoColors.TextSecondary
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = SolcoColors.TextMuted
    )
)

@Composable
fun SolcoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SolcoDarkScheme,
        typography = SolcoTypography,
        content = content
    )
}
