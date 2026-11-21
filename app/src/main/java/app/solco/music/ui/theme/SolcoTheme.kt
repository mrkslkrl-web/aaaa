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
    val BgDeep = Color(0xFF07070D)
    val BgSurface = Color(0xFF10101A)
    val CardBg = Color(0xFF151523)
    val CardElevated = Color(0xFF1C1C2E)
    val Border = Color(0xFF28283E)
    val BorderGlow = Color(0x449D7BFF)

    val TextPrimary = Color(0xFFF6F6FB)
    val TextSecondary = Color(0xFFA9A9C2)
    val TextMuted = Color(0xFF6E6E8A)

    val AccentViolet = Color(0xFF9D7BFF)
    val AccentCyan = Color(0xFF38D9F5)
    val AccentPink = Color(0xFFFF5C8D)
    val AccentMint = Color(0xFF2EC4B6)
    val AccentAmber = Color(0xFFFFC15E)
    val AccentCoral = Color(0xFFFF5C77)
    val AccentGold = Color(0xFFF7CE5B)

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
    onPrimary = Color(0xFF0A0814),
    secondary = SolcoColors.AccentCyan,
    onSecondary = Color(0xFF061318),
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
