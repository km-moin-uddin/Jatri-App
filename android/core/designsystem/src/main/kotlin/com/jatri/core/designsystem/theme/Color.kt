package com.jatri.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand + neutral palette — keep in sync with design/tokens.json.
object JatriPalette {
    // Brand (logo, splash, small accents)
    val OceanTeal = Color(0xFF0E7C86)
    val DeepTeal = Color(0xFF095A61)
    val MistTeal = Color(0xFFE3F4F5)
    val SunsetOrange = Color(0xFFFF7A45)

    // Transport modes
    val SkyBlue = Color(0xFF2D7FF9)
    val LeafGreen = Color(0xFF1FA463)
    val RailMaroon = Color(0xFFB23A48)

    // Status
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFDC2626)

    // Soft neutral UI (light): grey canvas, lighter cards, near-black pills.
    val Canvas = Color(0xFFECEDEF)
    val Card = Color(0xFFF7F8F9)
    val CardHigh = Color(0xFFFFFFFF)
    val Ink = Color(0xFF15181B)
    val Slate = Color(0xFF5B6670)
    val Line = Color(0xFFDCDFE3)
    val White = Color(0xFFFFFFFF)

    // Dark
    val DarkCanvas = Color(0xFF111315)
    val DarkCard = Color(0xFF1C1F22)
    val DarkCardHigh = Color(0xFF262A2E)
    val DarkText = Color(0xFFECEFF1)
    val DarkTextSecondary = Color(0xFFA3ABB3)
    val DarkLine = Color(0xFF2E3337)
    val DarkAccent = Color(0xFFFF8F63)
}

/**
 * `primary` is the near-black "pill" colour used for anything selected (chips, tabs, dates, small
 * buttons). Orange stays reserved for the main money/search actions (see [JatriExtendedColors.cta]).
 */
internal val LightColors = lightColorScheme(
    primary = JatriPalette.Ink,
    onPrimary = JatriPalette.White,
    primaryContainer = Color(0xFFDFE2E6),
    onPrimaryContainer = JatriPalette.Ink,
    secondary = JatriPalette.SunsetOrange,
    onSecondary = JatriPalette.Ink,
    secondaryContainer = Color(0xFFFFE7DC),
    onSecondaryContainer = Color(0xFF7A2E10),
    tertiary = JatriPalette.OceanTeal,
    onTertiary = JatriPalette.White,
    tertiaryContainer = JatriPalette.MistTeal,
    onTertiaryContainer = JatriPalette.DeepTeal,
    background = JatriPalette.Canvas,
    onBackground = JatriPalette.Ink,
    surface = JatriPalette.Card,
    onSurface = JatriPalette.Ink,
    surfaceVariant = Color(0xFFE4E6E9),
    onSurfaceVariant = JatriPalette.Slate,
    surfaceContainerLowest = JatriPalette.CardHigh,
    surfaceContainerLow = JatriPalette.Card,
    surfaceContainer = JatriPalette.Card,
    surfaceContainerHigh = JatriPalette.CardHigh,
    surfaceContainerHighest = Color(0xFFE4E6E9),
    outline = Color(0xFFC3C8CE),
    outlineVariant = JatriPalette.Line,
    error = JatriPalette.Error,
    onError = JatriPalette.White,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = Color(0xFF7F1D1D),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F3F4),
    onPrimary = JatriPalette.Ink,
    primaryContainer = JatriPalette.DarkCardHigh,
    onPrimaryContainer = JatriPalette.DarkText,
    secondary = JatriPalette.DarkAccent,
    onSecondary = JatriPalette.Ink,
    secondaryContainer = Color(0xFF5A2A16),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFF3FB8C2),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF0B4A50),
    onTertiaryContainer = Color(0xFFB8ECF0),
    background = JatriPalette.DarkCanvas,
    onBackground = JatriPalette.DarkText,
    surface = JatriPalette.DarkCard,
    onSurface = JatriPalette.DarkText,
    surfaceVariant = JatriPalette.DarkCardHigh,
    onSurfaceVariant = JatriPalette.DarkTextSecondary,
    surfaceContainerLowest = JatriPalette.DarkCanvas,
    surfaceContainerLow = JatriPalette.DarkCard,
    surfaceContainer = JatriPalette.DarkCard,
    surfaceContainerHigh = JatriPalette.DarkCardHigh,
    surfaceContainerHighest = Color(0xFF30353A),
    outline = Color(0xFF4A5258),
    outlineVariant = JatriPalette.DarkLine,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFECACA),
)
