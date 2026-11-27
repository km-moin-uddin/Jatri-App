package com.jatri.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.LoadingState
import com.jatri.core.designsystem.component.PriceSummaryBar
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppError
import com.jatri.core.ui.label
import com.jatri.core.ui.text

@Composable
internal fun TrainPassengerRoute(onBack: () -> Unit, onHeld: (String) -> Unit, viewModel: TrainPassengerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state.error?.takeIf { state.seatMap != null }?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = if ((error as? AppError.Api)?.code == "price_changed") {
                { Text(stringResource(R.string.booking_price_changed_title)) }
            } else {
                null
            },
            text = { Text(error.text()) },
            confirmButton = {
                if (state.mustReselect) {
                    TextButton(onClick = { viewModel.dismissError(); onBack() }) { Text(stringResource(R.string.booking_choose_again)) }
                } else {
                    TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.booking_ok)) }
                }
            },
        )
    }
    TrainPassengerScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onChange = viewModel::update,
        onPhoneChange = viewModel::setPhone,
        onEmailChange = viewModel::setEmail,
        onSubmit = { viewModel.submit(onHeld) },
    )
}

/** Stateless train passenger form, also used by screenshot tests. */
@Composable
fun TrainPassengerScreen(
    state: TrainPassengerUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onChange: (Int, (PassengerForm) -> PassengerForm) -> Unit = { _, _ -> },
    onPhoneChange: (String) -> Unit = {},
    onEmailChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    val lang = JatriTheme.language
    val map = state.seatMap
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            JatriTopAppBar(
                title = stringResource(R.string.booking_title),
                subtitle = map?.let { "${it.trip.trainName} · ${it.trainClass.label()}" },
                onBack = onBack,
            )
        },
        bottomBar = {
            if (map != null) {
                PriceSummaryBar(
                    label = stringResource(R.string.booking_total, state.passengers.size.localized(lang)),
                    price = state.total.format(lang),
                    buttonText = stringResource(R.string.booking_continue),
                    onClick = onSubmit,
                    loading = state.submitting,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            map == null -> ErrorState(onRetry = onRetry, message = state.error?.text(), modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(stringResource(R.string.booking_train_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
                state.passengers.forEachIndexed { i, p ->
                    val adult = (p.age.toIntOrNull() ?: 0) >= TRAIN_NID_AGE
                    PassengerCard(
                        index = i,
                        form = p,
                        onChange = { t -> onChange(i, t) },
                        ageLabel = stringResource(R.string.booking_age_required),
                        nidLabel = stringResource(if (adult) R.string.booking_train_nid else R.string.booking_train_nid_child),
                        nidError = stringResource(if (adult && p.nid.isEmpty()) R.string.booking_train_nid_required else R.string.booking_nid_error),
                        saved = state.travellers,
                    )
                }
                JatriCard {
                    Text(stringResource(R.string.booking_contact), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    JatriTextField(
                        value = state.phone,
                        onValueChange = onPhoneChange,
                        label = stringResource(R.string.booking_phone),
                        error = if (state.phoneError) stringResource(R.string.booking_phone_error) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    )
                    JatriTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = stringResource(R.string.booking_email),
                        error = if (state.emailError) stringResource(R.string.booking_email_error) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    )
                }
                Text(stringResource(R.string.booking_hold_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}
