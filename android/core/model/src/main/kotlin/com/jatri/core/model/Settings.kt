package com.jatri.core.model

enum class AppLanguage(val tag: String) {
    BANGLA("bn"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: BANGLA
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val language: AppLanguage = AppLanguage.BANGLA,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
)
