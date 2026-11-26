package com.jatri.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jatri.core.data.repository.UserSettingsRepository
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.ThemeMode
import com.jatri.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setLanguage(language: AppLanguage) = viewModelScope.launch { settingsRepository.setLanguage(language) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
}
