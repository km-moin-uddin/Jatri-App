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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.designsystem.component.JatriPrimaryButton
import com.jatri.core.designsystem.component.JatriTextButton
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay

/** Bangladeshi mobile: 01 + operator digit 3-9 + 8 digits. */
internal fun isValidBdPhone(phone: String): Boolean = Regex("^01[3-9]\\d{8}$").matches(phone)

/** Converts Bangla digits typed on a Bangla keyboard to ASCII. */
internal fun String.toAsciiDigits(): String = map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")

@Composable
private fun AuthScaffold(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Scaffold(topBar = { JatriTopAppBar(title = "", onBack = onBack) }) { padding ->
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
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(Spacing.xs))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
fun LoginScreen(onBack: () -> Unit, onCodeSent: (String) -> Unit, onContinueAsGuest: () -> Unit) {
    var phone by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val submit = {
        if (isValidBdPhone(phone)) onCodeSent(phone) else showError = true
    }
    AuthScaffold(onBack = onBack) {
        AuthHeader(stringResource(R.string.auth_login_title), stringResource(R.string.auth_login_body))
        JatriTextField(
            value = phone,
            onValueChange = { input ->
                phone = input.toAsciiDigits().filter(Char::isDigit).take(11)
                showError = false
            },
            label = stringResource(R.string.auth_phone_label),
            placeholder = stringResource(R.string.auth_phone_hint),
            prefix = { Text("+88 ") },
            error = if (showError) stringResource(R.string.auth_phone_error) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Spacer(Modifier.height(Spacing.md))
        JatriPrimaryButton(text = stringResource(R.string.auth_send_code), onClick = submit)
        Spacer(Modifier.height(Spacing.xs))
        JatriTextButton(text = stringResource(R.string.auth_continue_guest), onClick = onContinueAsGuest)
        Text(stringResource(R.string.auth_guest_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.auth_terms), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
    }
}

private const val OTP_LENGTH = 6
private const val RESEND_SECONDS = 60

/**
 * OTP entry. Phase 2 adds the SMS Retriever API so the code fills itself, and the real check.
 */
@Composable
fun OtpScreen(phone: String, onBack: () -> Unit, onVerified: () -> Unit) {
    val lang = JatriTheme.language
    var code by rememberSaveable { mutableStateOf("") }
    var secondsLeft by rememberSaveable { mutableIntStateOf(RESEND_SECONDS) }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }
    AuthScaffold(onBack = onBack) {
        AuthHeader(
            stringResource(R.string.auth_otp_title),
            stringResource(R.string.auth_otp_body, "+88 $phone".localizeDigits(lang)),
        )
        JatriTextField(
            value = code,
            onValueChange = { code = it.toAsciiDigits().filter(Char::isDigit).take(OTP_LENGTH) },
            label = stringResource(R.string.auth_otp_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.length == OTP_LENGTH) onVerified() }),
        )
        Text(stringResource(R.string.auth_dev_otp_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.warning)
        Spacer(Modifier.height(Spacing.md))
        JatriPrimaryButton(text = stringResource(R.string.auth_verify), onClick = onVerified, enabled = code.length == OTP_LENGTH)
        Spacer(Modifier.height(Spacing.xs))
        if (secondsLeft > 0) {
            val mmss = "%d:%02d".format(secondsLeft / 60, secondsLeft % 60).localizeDigits(lang)
            Text(stringResource(R.string.auth_resend_in, mmss), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
        } else {
            JatriTextButton(text = stringResource(R.string.auth_resend), onClick = { secondsLeft = RESEND_SECONDS })
        }
        JatriTextButton(text = stringResource(R.string.auth_change_number), onClick = onBack)
    }
}

@Composable
fun NameEntryScreen(onBack: () -> Unit, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val submit = { if (name.isNotBlank()) onDone() else showError = true }
    AuthScaffold(onBack = onBack) {
        AuthHeader(stringResource(R.string.auth_name_title), stringResource(R.string.auth_name_body))
        JatriTextField(
            value = name,
            onValueChange = {
                name = it.take(80)
                showError = false
            },
            label = stringResource(R.string.auth_name_label),
            error = if (showError) stringResource(R.string.auth_name_error) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Spacer(Modifier.height(Spacing.md))
        JatriPrimaryButton(text = stringResource(R.string.auth_finish), onClick = submit)
    }
}
