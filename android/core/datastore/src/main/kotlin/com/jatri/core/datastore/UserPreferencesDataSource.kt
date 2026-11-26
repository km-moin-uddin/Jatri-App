package com.jatri.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.ThemeMode
import com.jatri.core.model.UserSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferences: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Non-sensitive app settings (language, theme, onboarding). Tokens do NOT go here —
 * they will live in encrypted storage from Phase 2.
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme_mode")
        val onboardingDone = booleanPreferencesKey("onboarding_completed")
        val notifications = booleanPreferencesKey("notifications_enabled")
    }

    val settings: Flow<UserSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            UserSettings(
                language = AppLanguage.fromTag(prefs[Keys.language]),
                themeMode = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                onboardingCompleted = prefs[Keys.onboardingDone] ?: false,
                notificationsEnabled = prefs[Keys.notifications] ?: true,
            )
        }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.language] = language.tag }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.theme] = mode.name }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        dataStore.edit { it[Keys.onboardingDone] = done }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.notifications] = enabled }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providesUserPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.userPreferences
}
