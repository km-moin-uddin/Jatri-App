package com.jatri.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jatri.core.designsystem.R
import com.jatri.core.model.AppLanguage

/** Bundled Hind Siliguri — covers Bangla and Latin, used when the app is in Bangla. */
val HindSiliguri = FontFamily(
    Font(R.font.hindsiliguri_regular, FontWeight.Normal),
    Font(R.font.hindsiliguri_medium, FontWeight.Medium),
    Font(R.font.hindsiliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hindsiliguri_bold, FontWeight.Bold),
)

/** Bundled Plus Jakarta Sans — bold geometric display face for English headlines. */
val PlusJakartaSans = FontFamily(
    Font(R.font.plusjakarta_semibold, FontWeight.SemiBold),
    Font(R.font.plusjakarta_bold, FontWeight.Bold),
    Font(R.font.plusjakarta_extrabold, FontWeight.ExtraBold),
)

/** Bundled Inter — used for English text and numbers. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Type scale. Big, bold, left-aligned headlines (display face) over calm Inter / Hind Siliguri
 * body text. Bangla glyphs are taller, so Bangla gets a little more line height.
 * Prices on result cards use titleLarge/headlineSmall; screen titles use headlineMedium.
 */
internal fun jatriTypography(language: AppLanguage): Typography {
    val bangla = language == AppLanguage.BANGLA
    val text = if (bangla) HindSiliguri else Inter
    val display = if (bangla) HindSiliguri else PlusJakartaSans
    val lh = if (bangla) 1.6f else 1.5f
    val tight = if (bangla) 1.35f else 1.2f
    fun style(family: FontFamily, size: Int, weight: FontWeight, lineHeight: Float = tight, tracking: Float = 0f) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.em,
        letterSpacing = tracking.sp,
    )
    val headTracking = if (bangla) 0f else -0.5f
    return Typography(
        displayLarge = style(display, 36, FontWeight.ExtraBold, tracking = headTracking),
        displayMedium = style(display, 32, FontWeight.ExtraBold, tracking = headTracking),
        displaySmall = style(display, 28, FontWeight.Bold, tracking = headTracking),
        headlineLarge = style(display, 26, FontWeight.Bold, tracking = headTracking),
        headlineMedium = style(display, 22, FontWeight.Bold, tracking = headTracking),
        headlineSmall = style(display, 20, FontWeight.Bold),
        titleLarge = style(text, 18, FontWeight.SemiBold),
        titleMedium = style(text, 16, FontWeight.SemiBold),
        titleSmall = style(text, 14, FontWeight.SemiBold),
        bodyLarge = style(text, 16, FontWeight.Normal, lh),
        bodyMedium = style(text, 14, FontWeight.Normal, lh),
        bodySmall = style(text, 12, FontWeight.Normal, lh),
        labelLarge = style(text, 14, FontWeight.Medium),
        labelMedium = style(text, 12, FontWeight.Medium),
        labelSmall = style(text, 11, FontWeight.Medium),
    )
}
