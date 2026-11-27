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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
import com.jatri.core.model.AppError
import com.jatri.core.model.Gender
import com.jatri.core.model.PassengerType
import com.jatri.core.ui.flight.label
import com.jatri.core.ui.text

@Composable
internal fun FlightPassengerRoute(onBack: () -> Unit, onHeld: (String) -> Unit, viewModel: FlightPassengerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state.error?.takeIf { state.offer != null }?.let { error ->
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
                    TextButton(onClick = { viewModel.dismissError(); onBack() }) { Text(stringResource(R.string.booking_flight_choose_again)) }
                } else {
                    TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.booking_ok)) }
                }
            },
        )
    }
    FlightPassengerScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onChange = viewModel::update,
        onPhoneChange = viewModel::setPhone,
        onEmailChange = viewModel::setEmail,
        onSubmit = { viewModel.submit(onHeld) },
    )
}

/** Stateless flight passenger form, also used by screenshot tests. */
@Composable
fun FlightPassengerScreen(
    state: FlightPassengerUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onChange: (Int, (FlightPassengerForm) -> FlightPassengerForm) -> Unit = { _, _ -> },
    onPhoneChange: (String) -> Unit = {},
    onEmailChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    val lang = JatriTheme.language
    val offer = state.offer
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = stringResource(R.string.booking_title), onBack = onBack) },
        bottomBar = {
            if (offer != null) {
                PriceSummaryBar(
                    label = stringResource(R.string.booking_flight_total, state.passengers.size.localized(lang)),
                    price = offer.price.format(lang),
                    buttonText = stringResource(R.string.booking_continue),
                    onClick = onSubmit,
                    loading = state.submitting,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            offer == null -> ErrorState(onRetry = onRetry, message = state.error?.text(), modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(if (offer.international) R.string.booking_flight_note_intl else R.string.booking_flight_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = JatriTheme.colors.textSecondary,
                )
                val numbers = mutableMapOf<PassengerType, Int>()
                state.passengers.forEachIndexed { i, p ->
                    val n = (numbers[p.type] ?: 0) + 1
                    numbers[p.type] = n
                    FlightPassengerCard(
                        title = "${p.type.label()} ${n.localized(lang)}",
                        form = p,
                        international = offer.international,
                        onChange = { t -> onChange(i, t) },
                        saved = state.travellers.fitting(p.type, offer.departAt.toLocalDate()),
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
                Text(stringResource(R.string.booking_flight_hold_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun FlightPassengerCard(
    title: String,
    form: FlightPassengerForm,
    international: Boolean,
    onChange: ((FlightPassengerForm) -> FlightPassengerForm) -> Unit,
    saved: List<com.jatri.core.model.SavedTraveller> = emptyList(),
) {
    JatriCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusChip(
                stringResource(
                    when (form.type) {
                        PassengerType.ADULT -> R.string.booking_age_adult
                        PassengerType.CHILD -> R.string.booking_age_child
                        PassengerType.INFANT -> R.string.booking_age_infant
                    },
                ),
                StatusTone.Neutral,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        SavedTravellerChips(saved) { t -> onChange { it.fillFrom(t, international) } }
        JatriTextField(
            value = form.givenName,
            onValueChange = { v -> onChange { it.copy(givenName = v.take(40), givenNameError = null) } },
            label = stringResource(R.string.booking_given_name),
            error = form.givenNameError?.let { fieldError(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
        )
        JatriTextField(
            value = form.surname,
            onValueChange = { v -> onChange { it.copy(surname = v.take(40), surnameError = null) } },
            label = stringResource(R.string.booking_surname),
            error = form.surnameError?.let { fieldError(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
        )
        Text(stringResource(R.string.booking_gender), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(vertical = Spacing.xs)) {
            // Airline systems take male/female only (as printed on the passport).
            listOf(Gender.MALE, Gender.FEMALE).forEach { g ->
                PillChip(
                    text = stringResource(if (g == Gender.MALE) R.string.booking_male else R.string.booking_female),
                    selected = form.gender == g,
                    onClick = { onChange { it.copy(gender = g, genderError = false) } },
                )
            }
        }
        if (form.genderError) {
            Text(stringResource(R.string.booking_gender_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        JatriTextField(
            value = form.dob,
            onValueChange = { v -> onChange { it.copy(dob = v.filter(Char::isDigit).take(8), dobError = null) } },
            label = stringResource(R.string.booking_dob),
            placeholder = stringResource(R.string.booking_date_hint),
            error = form.dobError?.let { fieldError(it) },
            visualTransformation = DateDigitsTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
        JatriTextField(
            value = form.documentNo,
            onValueChange = { v -> onChange { it.copy(documentNo = v.filter(Char::isLetterOrDigit).uppercase().take(17), documentError = null) } },
            label = stringResource(if (international) R.string.booking_passport_no else R.string.booking_document_optional),
            error = form.documentError?.let { fieldError(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
        )
        if (international) {
            JatriTextField(
                value = form.expiry,
                onValueChange = { v -> onChange { it.copy(expiry = v.filter(Char::isDigit).take(8), expiryError = null) } },
                label = stringResource(R.string.booking_passport_expiry),
                placeholder = stringResource(R.string.booking_date_hint),
                error = form.expiryError?.let { fieldError(it) },
                visualTransformation = DateDigitsTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            JatriTextField(
                value = form.nationality,
                onValueChange = { v -> onChange { it.copy(nationality = v.filter(Char::isLetter).uppercase().take(2), nationalityError = false) } },
                label = stringResource(R.string.booking_nationality),
                error = if (form.nationalityError) stringResource(R.string.booking_nationality_error) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
            )
        }
    }
}

@Composable
private fun fieldError(error: FlightFieldError): String = stringResource(
    when (error) {
        FlightFieldError.REQUIRED -> R.string.booking_required
        FlightFieldError.LATIN_ONLY -> R.string.booking_latin_only
        FlightFieldError.DATE_INVALID -> R.string.booking_date_invalid
        FlightFieldError.AGE_MISMATCH -> R.string.booking_age_mismatch
        FlightFieldError.PASSPORT_INVALID -> R.string.booking_passport_invalid
        FlightFieldError.PASSPORT_EXPIRY -> R.string.booking_passport_expiry_short
        FlightFieldError.DOCUMENT_INVALID -> R.string.booking_document_invalid
    },
)

/** Shows typed digits "15081990" as "15/08/1990". */
internal object DateDigitsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val out = buildString {
            d.forEachIndexed { i, c ->
                append(c)
                if ((i == 1 || i == 3) && i != d.lastIndex) append('/')
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + (if (d.length > 2 && offset > 2) 1 else 0) + (if (d.length > 4 && offset > 4) 1 else 0)

            override fun transformedToOriginal(offset: Int): Int =
                (offset - (if (d.length > 2 && offset > 2) 1 else 0) - (if (d.length > 4 && offset > 5) 1 else 0)).coerceIn(0, d.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
