package com.jatri.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.di.MockFlag
import com.jatri.feature.auth.navigation.OtpDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FormState(
    val loading: Boolean = false,
    val error: AppError? = null,
    val inputError: Boolean = false,
)

private suspend fun <T> FormStateHolder.submit(block: suspend () -> T): T? {
    state.update { it.copy(loading = true, error = null, inputError = false) }
    return try {
        block().also { state.update { s -> s.copy(loading = false) } }
    } catch (e: AppException) {
        state.update { it.copy(loading = false, error = e.error) }
        null
    }
}

private interface FormStateHolder {
    val state: MutableStateFlow<FormState>
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel(), FormStateHolder {
    override val state = MutableStateFlow(FormState())
    val uiState: StateFlow<FormState> = state.asStateFlow()

    fun sendCode(rawPhone: String, onSent: (phone: String) -> Unit) {
        if (!isValidBdPhone(rawPhone)) {
            state.update { it.copy(inputError = true) }
            return
        }
        viewModelScope.launch { submit { auth.sendOtp(rawPhone) }?.let { onSent(it.phone) } }
    }

    fun clearError() = state.update { it.copy(error = null, inputError = false) }
}

@HiltViewModel
class OtpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auth: AuthRepository,
    mockFlag: MockFlag,
) : ViewModel(), FormStateHolder {
    val phone: String = savedStateHandle.toRoute<OtpDestination>().phone
    val usingMockBackend: Boolean = mockFlag.enabled
    override val state = MutableStateFlow(FormState())
    val uiState: StateFlow<FormState> = state.asStateFlow()

    /** @param onVerified receives true for a new user (ask their name next). */
    fun verify(code: String, onVerified: (isNewUser: Boolean) -> Unit) {
        if (state.value.loading) return
        viewModelScope.launch { submit { auth.verifyOtp(phone, code) }?.let(onVerified) }
    }

    fun resend(onSent: () -> Unit) {
        viewModelScope.launch { submit { auth.sendOtp(phone) }?.let { onSent() } }
    }

    fun clearError() = state.update { it.copy(error = null) }
}

@HiltViewModel
class NameEntryViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel(), FormStateHolder {
    override val state = MutableStateFlow(FormState())
    val uiState: StateFlow<FormState> = state.asStateFlow()

    fun save(name: String, onDone: () -> Unit) {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) {
            state.update { it.copy(inputError = true) }
            return
        }
        viewModelScope.launch { submit { auth.updateProfile(name = clean) }?.let { onDone() } }
    }
}
