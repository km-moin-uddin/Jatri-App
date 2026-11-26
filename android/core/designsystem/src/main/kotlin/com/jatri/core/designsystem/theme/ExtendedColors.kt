package com.jatri.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours Material 3 has no slot for: CTA, brand teal, transport modes, status, card border. */
@Immutable
data class JatriExtendedColors(
    val cta: Color,
    val onCta: Color,
    val brand: Color,
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
    /** Thin light edge on cards — gives the soft "glass" look without heavy shadows. */
    val cardBorder: Color,
)

internal val LightExtendedColors = JatriExtendedColors(
    cta = JatriPalette.SunsetOrange,
    onCta = JatriPalette.Ink, // 6.5:1 — white on this orange would fail WCAG AA
    brand = JatriPalette.OceanTeal,
    flight = JatriPalette.SkyBlue,
    bus = JatriPalette.LeafGreen,
    train = JatriPalette.RailMaroon,
    success = Color(0xFF15803D),
    successContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFB45309), // amber darkened to pass AA on light cards
    warningContainer = Color(0xFFFEF3C7),
    danger = JatriPalette.Error,
    dangerContainer = Color(0xFFFDE8E8),
    divider = JatriPalette.Line,
    textSecondary = JatriPalette.Slate,
    cardBorder = Color(0xCCFFFFFF),
)

internal val DarkExtendedColors = JatriExtendedColors(
    cta = JatriPalette.DarkAccent,
    onCta = JatriPalette.Ink,
    brand = Color(0xFF3FB8C2),
    flight = Color(0xFF6EA8FF),
    bus = Color(0xFF4CC58A),
    train = Color(0xFFE0707D),
    success = Color(0xFF4ADE80),
    successContainer = Color(0xFF14361F),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF3D2E08),
    danger = Color(0xFFF87171),
    dangerContainer = Color(0xFF5C1414),
    divider = JatriPalette.DarkLine,
    textSecondary = JatriPalette.DarkTextSecondary,
    cardBorder = Color(0x14FFFFFF),
)

val LocalJatriColors = staticCompositionLocalOf { LightExtendedColors }
