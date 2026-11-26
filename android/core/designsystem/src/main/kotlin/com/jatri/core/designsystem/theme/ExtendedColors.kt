package com.jatri.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand colours Material 3 has no slot for: CTA, transport-mode colours and status colours. */
@Immutable
data class JatriExtendedColors(
    val cta: Color,
    val onCta: Color,
    val flight: Color,
    val bus: Color,
    val train: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val divider: Color,
    val textSecondary: Color,
    val topBar: Color,
    val onTopBar: Color,
)

internal val LightExtendedColors = JatriExtendedColors(
    cta = JatriPalette.SunsetOrange,
    onCta = JatriPalette.Ink,
    flight = JatriPalette.SkyBlue,
    bus = JatriPalette.LeafGreen,
    train = JatriPalette.RailMaroon,
    success = JatriPalette.Success,
    successContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFB45309), // Amber text darkened to pass AA on white
    warningContainer = Color(0xFFFEF3C7),
    danger = JatriPalette.Error,
    dangerContainer = Color(0xFFFDE8E8),
    divider = JatriPalette.LightGrey,
    textSecondary = JatriPalette.Slate,
    topBar = JatriPalette.OceanTeal,
    onTopBar = JatriPalette.White,
)

internal val DarkExtendedColors = JatriExtendedColors(
    cta = JatriPalette.DarkAccent,
    onCta = JatriPalette.Ink,
    flight = Color(0xFF6EA8FF),
    bus = Color(0xFF4CC58A),
    train = Color(0xFFE0707D),
    success = Color(0xFF4ADE80),
    successContainer = Color(0xFF14361F),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF3D2E08),
    danger = Color(0xFFF87171),
    dangerContainer = Color(0xFF5C1414),
    divider = JatriPalette.DarkDivider,
    textSecondary = JatriPalette.DarkTextSecondary,
    topBar = Color(0xFF0B4A50), // deep teal: on-brand, and separates the header from dark cards
    onTopBar = JatriPalette.DarkText,
)

val LocalJatriColors = staticCompositionLocalOf { LightExtendedColors }
