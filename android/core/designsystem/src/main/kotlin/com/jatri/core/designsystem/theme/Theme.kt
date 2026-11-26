package com.jatri.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.jatri.core.model.AppLanguage

/** The language the UI is currently shown in. Used for digits, prices and dates. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.BANGLA }

/**
 * Jatri's Material 3 theme. Dynamic colour is intentionally **off** so the brand stays consistent.
 *
 * @param language defaults to the language of the current resources configuration, which follows
 * the per-app locale set in Settings.
 */
@Composable
fun JatriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    language: AppLanguage = currentResourcesLanguage(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val typography = remember(language) { jatriTypography(language) }
    CompositionLocalProvider(
        LocalJatriColors provides extended,
        LocalAppLanguage provides language,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = JatriMaterialShapes,
            content = content,
        )
    }
}

@Composable
@ReadOnlyComposable
fun currentResourcesLanguage(): AppLanguage {
    val locales = LocalConfiguration.current.locales
    val tag = if (locales.isEmpty) null else locales[0].language
    return if (tag == "bn") AppLanguage.BANGLA else AppLanguage.ENGLISH
}

/** Shortcut accessors: `JatriTheme.colors.cta`, `JatriTheme.language`. */
object JatriTheme {
    val colors: JatriExtendedColors
        @Composable @ReadOnlyComposable get() = LocalJatriColors.current

    val language: AppLanguage
        @Composable @ReadOnlyComposable get() = LocalAppLanguage.current
}
