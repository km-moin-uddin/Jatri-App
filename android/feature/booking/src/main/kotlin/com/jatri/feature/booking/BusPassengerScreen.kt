package com.jatri.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.component.PriceSummaryBar
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Gender
import com.jatri.core.ui.text

@Composable
internal fun BusPassengerRoute(onBack: () -> Unit, onHeld: (String) -> Unit, viewModel: BusPassengerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lang = JatriTheme.language

    state.error?.takeIf { state.seatMap != null }?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = if ((error as? com.jatri.core.model.AppError.Api)?.code == "price_changed") {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = stringResource(R.string.booking_title), onBack = onBack) },
        bottomBar = {
            if (state.seatMap != null) {
                PriceSummaryBar(
                    label = stringResource(R.string.booking_total, state.passengers.size.localized(lang)),
                    price = state.total.format(lang),
                    buttonText = stringResource(R.string.booking_continue),
                    onClick = { viewModel.submit(onHeld) },
                    loading = state.submitting,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.seatMap == null -> ErrorState(onRetry = onBack, message = state.error?.text(), modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.passengers.forEachIndexed { i, p ->
                    PassengerCard(index = i, form = p, onChange = { t -> viewModel.update(i, t) })
                }
                JatriCard {
                    Text(stringResource(R.string.booking_contact), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    JatriTextField(
                        value = state.phone,
                        onValueChange = viewModel::setPhone,
                        label = stringResource(R.string.booking_phone),
                        error = if (state.phoneError) stringResource(R.string.booking_phone_error) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    )
                    JatriTextField(
                        value = state.email,
                        onValueChange = viewModel::setEmail,
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

@Composable
internal fun PassengerCard(
    index: Int,
    form: PassengerForm,
    onChange: ((PassengerForm) -> PassengerForm) -> Unit,
    ageLabel: String = stringResource(R.string.booking_age),
    nidLabel: String = stringResource(R.string.booking_nid_optional),
    nidError: String = stringResource(R.string.booking_nid_error),
    seatLabel: String = form.seatNo,
) {
    val lang = JatriTheme.language
    JatriCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.booking_passenger, (index + 1).localized(lang)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            StatusChip(stringResource(R.string.booking_seat, seatLabel), StatusTone.Neutral)
            if (form.ladiesSeat) StatusChip(stringResource(R.string.booking_ladies_seat), StatusTone.Info)
        }
        Spacer(Modifier.height(Spacing.xs))
        JatriTextField(
            value = form.name,
            onValueChange = { v -> onChange { it.copy(name = v.take(80), nameError = false) } },
            label = stringResource(R.string.booking_full_name),
            error = if (form.nameError) stringResource(R.string.booking_name_error) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        Text(stringResource(R.string.booking_gender), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(vertical = Spacing.xs)) {
            val options = if (form.ladiesSeat) listOf(Gender.FEMALE) else Gender.entries
            options.forEach { g ->
                PillChip(
                    text = stringResource(
                        when (g) {
                            Gender.MALE -> R.string.booking_male
                            Gender.FEMALE -> R.string.booking_female
                            Gender.OTHER -> R.string.booking_other
                        },
                    ),
                    selected = form.gender == g,
                    onClick = { onChange { it.copy(gender = g, genderError = false) } },
                )
            }
        }
        if (form.genderError) {
            Text(stringResource(R.string.booking_gender_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        JatriTextField(
            value = form.age,
            onValueChange = { v -> onChange { it.copy(age = v.filter(Char::isDigit).take(3), ageError = false) } },
            label = ageLabel,
            error = if (form.ageError) stringResource(R.string.booking_age_error) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
        JatriTextField(
            value = form.nid,
            onValueChange = { v -> onChange { it.copy(nid = v.filter(Char::isDigit).take(17), nidError = false) } },
            label = nidLabel,
            error = if (form.nidError) nidError else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        )
    }
}
