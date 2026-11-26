package com.jatri.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jatri.core.data.repository.UserSettingsRepository
import com.jatri.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val settings: UserSettings) : MainUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(settings: UserSettingsRepository) : ViewModel() {
    val uiState: StateFlow<MainUiState> = settings.settings
        .map<UserSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState.Loading)
}
