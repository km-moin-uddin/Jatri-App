package com.jatri.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.designsystem.component.JatriPrimaryButton
import com.jatri.core.designsystem.component.JatriTextButton
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppError
import com.jatri.core.ui.text
import kotlinx.coroutines.delay

/** Bangladeshi mobile: 01 + operator digit 3-9 + 8 digits. */
internal fun isValidBdPhone(phone: String): Boolean = Regex("^01[3-9]\\d{8}$").matches(phone)

/** Converts Bangla digits typed on a Bangla keyboard to ASCII. */
internal fun String.toAsciiDigits(): String = map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")

@Composable
private fun AuthScaffold(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = "", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
        ) { content() }
    }
}

@Composable
private fun AuthHeader(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(Spacing.xs))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
private fun ErrorLine(error: AppError?) {
    if (error == null) return
    Spacer(Modifier.height(Spacing.xs))
    Text(
        error.text(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
internal fun LoginRoute(onBack: () -> Unit, onCodeSent: (String) -> Unit, onContinueAsGuest: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onBack = onBack,
        onSubmit = { viewModel.sendCode(it, onCodeSent) },
        onPhoneEdited = viewModel::clearError,
        onContinueAsGuest = onContinueAsGuest,
    )
}

@Composable
fun LoginScreen(
    state: FormState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onPhoneEdited: () -> Unit,
    onContinueAsGuest: () -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    AuthScaffold(onBack = onBack) {
        AuthHeader(stringResource(R.string.auth_login_title), stringResource(R.string.auth_login_body))
        JatriTextField(
            value = phone,
            onValueChange = { input ->
                phone = input.toAsciiDigits().filter(Char::isDigit).take(11)
                onPhoneEdited()
            },
            label = stringResource(R.string.auth_phone_label),
            placeholder = stringResource(R.string.auth_phone_hint),
            prefix = { Text("+88 ") },
            error = if (state.inputError) stringResource(R.string.auth_phone_error) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(phone) }),
        )
        ErrorLine(state.error)
        Spacer(Modifier.height(Spacing.md))
        JatriPrimaryButton(text = stringResource(R.string.auth_send_code), onClick = { onSubmit(phone) }, loading = state.loading)
        Spacer(Modifier.height(Spacing.xs))
        JatriTextButton(text = stringResource(R.string.auth_continue_guest), onClick = onContinueAsGuest)
        Text(stringResource(R.string.auth_guest_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.auth_terms), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
    }
}

private const val OTP_LENGTH = 6
private const val RESEND_SECONDS = 60

@Composable
internal fun OtpRoute(onBack: () -> Unit, onVerified: (isNewUser: Boolean) -> Unit, viewModel: OtpViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var secondsLeft by rememberSaveable { mutableIntStateOf(RESEND_SECONDS) }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }
    // Auto-fill and submit when the SMS arrives.
    SmsCodeListener { received ->
        code = received
        viewModel.verify(received, onVerified)
    }
    OtpScreen(
        phone = viewModel.phone,
        code = code,
        state = state,
        secondsLeft = secondsLeft,
        showDemoHint = viewModel.usingMockBackend,
        onCodeChange = { input ->
            code = input.toAsciiDigits().filter(Char::isDigit).take(OTP_LENGTH)
            viewModel.clearError()
            if (code.length == OTP_LENGTH) viewModel.verify(code, onVerified)
        },
        onVerify = { viewModel.verify(code, onVerified) },
        onResend = { viewModel.resend { secondsLeft = RESEND_SECONDS } },
        onBack = onBack,
    )
}

@Composable
fun OtpScreen(
    phone: String,
    code: String,
    state: FormState,
    secondsLeft: Int,
    showDemoHint: Boolean,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    val lang = JatriTheme.language
    AuthScaffold(onBack = onBack) {
        AuthHeader(
            stringResource(R.string.auth_otp_title),
            stringResource(R.string.auth_otp_body, "+88 $phone".localizeDigits(lang)),
        )
        JatriTextField(
            value = code,
            onValueChange = onCodeChange,
            label = stringResource(R.string.auth_otp_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.length == OTP_LENGTH) onVerify() }),
        )
        ErrorLine(state.error)
        if (showDemoHint) {
            Text(stringResource(R.string.auth_dev_otp_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.warning)
        }
        Spacer(Modifier.height(Spacing.md))
        JatriPrimaryButton(
            text = stringResource(R.string.auth_verify),
            onClick = onVerify,
            enabled = code.length == OTP_LENGTH,
            loading = state.loading,
        )
        Spacer(Modifier.height(Spacing.xs))
        if (secondsLeft > 0) {
            val mmss = "%d:%02d".format(secondsLeft / 60, secondsLeft % 60).localizeDigits(lang)
            Text(stringResource(R.string.auth_resend_in, mmss), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
        } else {
            JatriTextButton(text = stringResource(R.string.auth_resend), onClick = onResend)
        }
        JatriTextButton(text = stringResource(R.string.auth_change_number), onClick = onBack)
    }
}

@Composable
internal fun NameEntryRoute(onBack: () -> Unit, onDone: () -> Unit, viewModel: NameEntryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    AuthScaffold(onBack = onBack) {
        AuthHeader(stringResource(R.string.auth_name_title), stringResource(R.string.auth_name_body))
        JatriTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = stringResource(R.string.auth_name_label),
            error = if (state.inputError) stringResource(R.string.auth_name_error) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.save(name, onDone) }),
        )
        ErrorLine(state.error)
        Spacer(Modifier.height(Spacing.md))
        JatriPrimaryButton(text = stringResource(R.string.auth_finish), onClick = { viewModel.save(name, onDone) }, loading = state.loading)
    }
}
