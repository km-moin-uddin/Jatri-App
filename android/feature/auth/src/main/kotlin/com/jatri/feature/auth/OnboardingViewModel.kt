package com.jatri.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jatri.core.data.repository.UserSettingsRepository
import com.jatri.core.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: UserSettingsRepository,
) : ViewModel() {
    val language: StateFlow<AppLanguage> = settings.settings.map { it.language }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.BANGLA)

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun finish(then: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingCompleted(true)
            then()
        }
    }
}
