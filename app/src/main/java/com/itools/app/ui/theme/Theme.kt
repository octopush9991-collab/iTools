package com.itools.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object IToolsPalette {
    val Accent = Color(0xFFCFFF47)
    val AccentDeep = Color(0xFF9BCB22)
    val Ink = Color(0xFF080B0D)
    val Panel = Color(0xFF12171A)
    val PanelRaised = Color(0xFF1A2125)
    val Outline = Color(0xFF2D383D)
    val TextPrimary = Color(0xFFF1F6F2)
    val TextMuted = Color(0xFF8D9A9F)
    val Danger = Color(0xFFFF6B6B)
    val SecondaryBlue = Color(0xFF72C7FF)
    val Audio = Color(0xFF58D6C1)
    val TextTrack = Color(0xFF7DA8FF)
    val StickerTrack = Color(0xFFFFB35C)
    val CaptionTrack = Color(0xFFCB8CFF)
    val OverlayTrack = Color(0xFFFF7A9A)
    val Video = Color(0xFF6F8CFF)
    val Playhead = Color(0xFFFF4D6D)
}

private val DarkScheme = darkColorScheme(
    primary = IToolsPalette.Accent,
    onPrimary = Color(0xFF1B2500),
    primaryContainer = Color(0xFF354700),
    onPrimaryContainer = IToolsPalette.Accent,
    secondary = Color(0xFF9FD5FF),
    onSecondary = Color(0xFF00293A),
    tertiary = Color(0xFFFFB35C),
    background = IToolsPalette.Ink,
    onBackground = IToolsPalette.TextPrimary,
    surface = IToolsPalette.Panel,
    onSurface = IToolsPalette.TextPrimary,
    surfaceVariant = IToolsPalette.PanelRaised,
    onSurfaceVariant = Color(0xFFB9C5C8),
    outline = IToolsPalette.Outline,
    error = IToolsPalette.Danger,
    onError = Color(0xFF2C0000)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4B6500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4F17E),
    onPrimaryContainer = Color(0xFF151F00),
    secondary = Color(0xFF3C637A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF845300),
    background = Color(0xFFF6F8F3),
    onBackground = Color(0xFF171B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171B16),
    surfaceVariant = Color(0xFFE9EDE3),
    onSurfaceVariant = Color(0xFF42493F),
    outline = Color(0xFF73796E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val IToolsTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        letterSpacing = (-1.2).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

private val IToolsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun IToolsTheme(
    themeSetting: String = "dark",
    content: @Composable () -> Unit
) {
    val dark = when (themeSetting) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = IToolsTypography,
        shapes = IToolsShapes,
        content = content
    )
}
