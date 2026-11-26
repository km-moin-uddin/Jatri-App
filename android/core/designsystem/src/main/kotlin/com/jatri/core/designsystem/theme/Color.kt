package com.jatri.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — keep in sync with design/tokens.json.
object JatriPalette {
    val OceanTeal = Color(0xFF0E7C86)
    val DeepTeal = Color(0xFF095A61)
    val MistTeal = Color(0xFFE3F4F5)
    val SunsetOrange = Color(0xFFFF7A45)
    val SkyBlue = Color(0xFF2D7FF9)
    val LeafGreen = Color(0xFF1FA463)
    val RailMaroon = Color(0xFFB23A48)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFDC2626)
    val OffWhite = Color(0xFFF7F9FA)
    val White = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1A1F24)
    val Slate = Color(0xFF5B6670)
    val LightGrey = Color(0xFFE5E9EC)

    // Dark mode
    val DarkBackground = Color(0xFF0F1416)
    val DarkSurface = Color(0xFF1A2124)
    val DarkSurfaceHigh = Color(0xFF232C30)
    val DarkPrimary = Color(0xFF3FB8C2)
    val DarkAccent = Color(0xFFFF8F63)
    val DarkText = Color(0xFFECEFF1)
    val DarkTextSecondary = Color(0xFFA9B4BC)
    val DarkDivider = Color(0xFF2C363A)
}

internal val LightColors = lightColorScheme(
    primary = JatriPalette.OceanTeal,
    onPrimary = JatriPalette.White,
    primaryContainer = JatriPalette.MistTeal,
    onPrimaryContainer = JatriPalette.DeepTeal,
    secondary = JatriPalette.SunsetOrange,
    // Ink on orange is 6.5:1 (WCAG AA); white on this orange would only be 2.6:1.
    onSecondary = JatriPalette.Ink,
    secondaryContainer = Color(0xFFFFE7DC),
    onSecondaryContainer = Color(0xFF7A2E10),
    tertiary = JatriPalette.SkyBlue,
    onTertiary = JatriPalette.White,
    background = JatriPalette.OffWhite,
    onBackground = JatriPalette.Ink,
    surface = JatriPalette.White,
    onSurface = JatriPalette.Ink,
    surfaceVariant = Color(0xFFF0F3F5),
    onSurfaceVariant = JatriPalette.Slate,
    surfaceContainerLowest = JatriPalette.White,
    surfaceContainerLow = JatriPalette.White,
    surfaceContainer = JatriPalette.White,
    surfaceContainerHigh = Color(0xFFF2F5F6),
    surfaceContainerHighest = Color(0xFFEBEFF1),
    outline = Color(0xFFC5CDD3),
    outlineVariant = JatriPalette.LightGrey,
    error = JatriPalette.Error,
    onError = JatriPalette.White,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = Color(0xFF7F1D1D),
)

internal val DarkColors = darkColorScheme(
    primary = JatriPalette.DarkPrimary,
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF0B4A50),
    onPrimaryContainer = Color(0xFFB8ECF0),
    secondary = JatriPalette.DarkAccent,
    onSecondary = JatriPalette.Ink,
    secondaryContainer = Color(0xFF5A2A16),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFF7FB0FF),
    onTertiary = Color(0xFF00285E),
    background = JatriPalette.DarkBackground,
    onBackground = JatriPalette.DarkText,
    surface = JatriPalette.DarkSurface,
    onSurface = JatriPalette.DarkText,
    surfaceVariant = JatriPalette.DarkSurfaceHigh,
    onSurfaceVariant = JatriPalette.DarkTextSecondary,
    surfaceContainerLowest = JatriPalette.DarkBackground,
    surfaceContainerLow = JatriPalette.DarkSurface,
    surfaceContainer = JatriPalette.DarkSurface,
    surfaceContainerHigh = JatriPalette.DarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF2A3438),
    outline = Color(0xFF4A565B),
    outlineVariant = JatriPalette.DarkDivider,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFECACA),
)
