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

/** Bundled Inter — used for English text and numbers. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Type scale from the design spec:
 * Display 24/SemiBold (prices) · Headline 20/SemiBold (screen titles) · Title 16/Medium
 * · Body 14/Regular, 1.5 line height · Label 12/Medium.
 * Bangla glyphs are taller, so Bangla gets a little more line height.
 */
internal fun jatriTypography(language: AppLanguage): Typography {
    val family = if (language == AppLanguage.BANGLA) HindSiliguri else Inter
    val lh = if (language == AppLanguage.BANGLA) 1.6f else 1.5f
    val tight = if (language == AppLanguage.BANGLA) 1.4f else 1.25f
    fun style(size: Int, weight: FontWeight, lineHeight: Float = tight) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.em,
        letterSpacing = 0.sp,
    )
    return Typography(
        displayLarge = style(32, FontWeight.SemiBold),
        displayMedium = style(28, FontWeight.SemiBold),
        displaySmall = style(24, FontWeight.SemiBold),
        headlineLarge = style(24, FontWeight.SemiBold),
        headlineMedium = style(22, FontWeight.SemiBold),
        headlineSmall = style(20, FontWeight.SemiBold),
        titleLarge = style(18, FontWeight.SemiBold),
        titleMedium = style(16, FontWeight.Medium),
        titleSmall = style(14, FontWeight.Medium),
        bodyLarge = style(16, FontWeight.Normal, lh),
        bodyMedium = style(14, FontWeight.Normal, lh),
        bodySmall = style(12, FontWeight.Normal, lh),
        labelLarge = style(14, FontWeight.Medium),
        labelMedium = style(12, FontWeight.Medium),
        labelSmall = style(11, FontWeight.Medium),
    )
}
