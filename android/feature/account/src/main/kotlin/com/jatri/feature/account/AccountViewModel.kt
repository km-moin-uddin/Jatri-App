package com.jatri.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.UserSettingsRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.SessionState
import com.jatri.core.model.ThemeMode
import com.jatri.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val session: StateFlow<SessionState> = auth.session

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    init {
        // Pick up name changes made on another device.
        viewModelScope.launch { if (auth.session.value is SessionState.LoggedIn) runCatching { auth.refreshProfile() } }
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsRepository.setLanguage(language)
        if (auth.session.value is SessionState.LoggedIn) runCatching { auth.updateProfile(language = language) }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }

    fun logout() = viewModelScope.launch { auth.logout() }

    fun deleteAccount() = viewModelScope.launch {
        _busy.value = true
        try {
            auth.deleteAccount()
        } catch (e: AppException) {
            _error.value = e.error
        } finally {
            _busy.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }
}

data class ProfileForm(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val saving: Boolean = false,
    val nameError: Boolean = false,
    val emailError: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _form = MutableStateFlow(
        (auth.session.value as? SessionState.LoggedIn)?.user.let {
            ProfileForm(name = it?.name.orEmpty(), email = it?.email.orEmpty(), phone = it?.phone.orEmpty())
        },
    )
    val form: StateFlow<ProfileForm> = _form.asStateFlow()

    fun onName(value: String) = _form.update { it.copy(name = value.take(80), nameError = false) }
    fun onEmail(value: String) = _form.update { it.copy(email = value.trim().take(254), emailError = false) }

    fun save(onSaved: () -> Unit) {
        val f = _form.value
        val nameOk = f.name.isNotBlank()
        val emailOk = f.email.isEmpty() || EMAIL.matches(f.email)
        if (!nameOk || !emailOk) {
            _form.update { it.copy(nameError = !nameOk, emailError = !emailOk) }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            try {
                auth.updateProfile(name = f.name.trim(), email = f.email.ifEmpty { null })
                _form.update { it.copy(saving = false) }
                onSaved()
            } catch (e: AppException) {
                _form.update { it.copy(saving = false, error = e.error) }
            }
        }
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
