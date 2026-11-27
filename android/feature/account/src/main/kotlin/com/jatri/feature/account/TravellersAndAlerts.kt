package com.jatri.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.repository.PriceAlertRepository
import com.jatri.core.data.repository.TravellerRepository
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.JatriBottomSheet
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.LoadingState
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.Gender
import com.jatri.core.model.Location
import com.jatri.core.model.PriceAlert
import com.jatri.core.model.SavedTraveller
import com.jatri.core.ui.color
import com.jatri.core.ui.icon
import com.jatri.core.ui.text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ---------------------------------------------------------------- saved travellers

data class TravellersUiState(
    val loading: Boolean = true,
    val items: List<SavedTraveller> = emptyList(),
    val error: AppError? = null,
    /** The traveller being added/edited (id null = new). */
    val editing: SavedTraveller? = null,
    val saving: Boolean = false,
    val saveError: AppError? = null,
)

@HiltViewModel
class TravellersViewModel @Inject constructor(private val repo: TravellerRepository) : ViewModel() {
    private val _state = MutableStateFlow(TravellersUiState())
    val state: StateFlow<TravellersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
            try {
                _state.update { it.copy(loading = false, items = repo.list()) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    fun edit(t: SavedTraveller?) = _state.update { it.copy(editing = t ?: SavedTraveller(null, "", gender = Gender.MALE), saveError = null) }
    fun close() = _state.update { it.copy(editing = null) }

    fun save(t: SavedTraveller) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = null) }
            try {
                repo.save(t)
                _state.update { it.copy(saving = false, editing = null, items = repo.list()) }
            } catch (e: AppException) {
                _state.update { it.copy(saving = false, saveError = e.error) }
            }
        }
    }

    fun delete(id: String) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }, editing = null) }
        viewModelScope.launch { runCatching { repo.delete(id) } }
    }
}

@Composable
internal fun TravellersRoute(onBack: () -> Unit, viewModel: TravellersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TravellersScreen(state, onBack, viewModel::load, viewModel::edit)
    state.editing?.let { TravellerSheet(it, state.saving, state.saveError, viewModel::save, viewModel::delete, viewModel::close) }
}

/** Family and friends saved for one-tap passenger forms. Stateless. */
@Composable
fun TravellersScreen(
    state: TravellersUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onEdit: (SavedTraveller?) -> Unit = {},
) {
    val lang = JatriTheme.language
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            JatriTopAppBar(
                title = stringResource(R.string.account_saved_travellers),
                onBack = onBack,
                actions = { IconButton(onClick = { onEdit(null) }) { Icon(JatriIcons.Add, stringResource(R.string.travellers_add)) } },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(onRetry = onRetry, message = state.error.text(), modifier = Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                title = stringResource(R.string.travellers_empty),
                message = stringResource(R.string.travellers_empty_body),
                icon = JatriIcons.Group,
                actionText = stringResource(R.string.travellers_add),
                onAction = { onEdit(null) },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item { Text(stringResource(R.string.travellers_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary) }
                items(state.items, key = { it.id.orEmpty() }) { t ->
                    JatriCard(Modifier.fillMaxWidth(), onClick = { onEdit(t) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.fullName, style = MaterialTheme.typography.titleMedium)
                                val details = listOfNotNull(
                                    t.dateOfBirth?.let { DateTimeFormats.longDate(it, lang) },
                                    t.nid?.let { stringResource(R.string.travellers_nid_short, "•••" + it.takeLast(4)).localizeDigits(lang) },
                                    t.passportNo?.let { stringResource(R.string.travellers_passport_short, it) },
                                )
                                if (details.isNotEmpty()) {
                                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
                                }
                            }
                            Icon(JatriIcons.ChevronRight, contentDescription = null, tint = JatriTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun parseDate(digits: String): LocalDate? = if (digits.length != 8) null else runCatching {
    LocalDate.of(digits.substring(4).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
}.getOrNull()

private fun LocalDate.digits() = "%02d%02d%04d".format(dayOfMonth, monthValue, year)

@Composable
private fun TravellerSheet(
    initial: SavedTraveller,
    saving: Boolean,
    error: AppError?,
    onSave: (SavedTraveller) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.fullName) }
    var given by remember { mutableStateOf(initial.givenName.orEmpty()) }
    var surname by remember { mutableStateOf(initial.surname.orEmpty()) }
    var gender by remember { mutableStateOf(initial.gender) }
    var dob by remember { mutableStateOf(initial.dateOfBirth?.digits().orEmpty()) }
    var nid by remember { mutableStateOf(initial.nid.orEmpty()) }
    var passport by remember { mutableStateOf(initial.passportNo.orEmpty()) }
    var expiry by remember { mutableStateOf(initial.passportExpiry?.digits().orEmpty()) }
    var tried by remember { mutableStateOf(false) }
    val latin = Regex("^[A-Za-z][A-Za-z .'-]*$")
    val nameErr = tried && name.trim().length < 2
    val givenErr = tried && given.isNotBlank() && !latin.matches(given.trim())
    val surnameErr = tried && surname.isNotBlank() && !latin.matches(surname.trim())
    val dobErr = tried && dob.isNotEmpty() && parseDate(dob)?.isAfter(LocalDate.now()) != false
    val nidErr = tried && nid.isNotEmpty() && nid.length !in setOf(10, 13, 17)
    val passportErr = tried && passport.isNotEmpty() && !Regex("^[A-Z0-9]{6,9}$").matches(passport)
    val expiryErr = tried && passport.isNotEmpty() && parseDate(expiry) == null
    JatriBottomSheet(
        title = stringResource(if (initial.id == null) R.string.travellers_add else R.string.travellers_edit),
        onDismiss = onDismiss,
        fullHeight = true,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            val req = stringResource(R.string.travellers_error)
            JatriTextField(name, { name = it.take(80) }, stringResource(R.string.travellers_full_name), error = if (nameErr) req else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                JatriTextField(given, { given = it.take(40) }, stringResource(R.string.travellers_given), Modifier.weight(1f), error = if (givenErr) req else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                JatriTextField(surname, { surname = it.take(40) }, stringResource(R.string.travellers_surname), Modifier.weight(1f), error = if (surnameErr) req else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(vertical = Spacing.xs)) {
                listOf(Gender.MALE to R.string.travellers_male, Gender.FEMALE to R.string.travellers_female, Gender.OTHER to R.string.travellers_other).forEach { (g, res) ->
                    PillChip(stringResource(res), selected = gender == g, onClick = { gender = g })
                }
            }
            JatriTextField(dob, { dob = it.filter(Char::isDigit).take(8) }, stringResource(R.string.travellers_dob), placeholder = "DD/MM/YYYY",
                error = if (dobErr) req else null, visualTransformation = DateMask, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            JatriTextField(nid, { nid = it.filter(Char::isDigit).take(17) }, stringResource(R.string.travellers_nid), error = if (nidErr) req else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                JatriTextField(passport, { passport = it.filter(Char::isLetterOrDigit).uppercase().take(9) }, stringResource(R.string.travellers_passport),
                    Modifier.weight(1f), error = if (passportErr) req else null, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                JatriTextField(expiry, { expiry = it.filter(Char::isDigit).take(8) }, stringResource(R.string.travellers_expiry), Modifier.weight(1f),
                    placeholder = "DD/MM/YYYY", error = if (expiryErr) req else null, visualTransformation = DateMask,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(Spacing.md))
            JatriCtaButton(
                text = stringResource(R.string.travellers_save),
                loading = saving,
                onClick = {
                    tried = true
                    val bad = name.trim().length < 2 || (given.isNotBlank() && !latin.matches(given.trim())) ||
                        (surname.isNotBlank() && !latin.matches(surname.trim())) ||
                        (dob.isNotEmpty() && parseDate(dob)?.isAfter(LocalDate.now()) != false) ||
                        (nid.isNotEmpty() && nid.length !in setOf(10, 13, 17)) ||
                        (passport.isNotEmpty() && (!Regex("^[A-Z0-9]{6,9}$").matches(passport) || parseDate(expiry) == null))
                    if (!bad) {
                        onSave(
                            initial.copy(
                                fullName = name.trim(), givenName = given.trim().ifBlank { null }, surname = surname.trim().ifBlank { null },
                                gender = gender, dateOfBirth = parseDate(dob), nid = nid.ifBlank { null }, passportNo = passport.ifBlank { null },
                                passportExpiry = if (passport.isBlank()) null else parseDate(expiry),
                            ),
                        )
                    }
                },
            )
            initial.id?.let { id ->
                com.jatri.core.designsystem.component.JatriTextButton(
                    text = stringResource(R.string.travellers_delete), onClick = { onDelete(id) }, color = JatriTheme.colors.danger,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** "15081990" shown as "15/08/1990". */
private object DateMask : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val d = text.text
        val out = buildString { d.forEachIndexed { i, c -> append(c); if ((i == 1 || i == 3) && i != d.lastIndex) append('/') } }
        val map = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (if (d.length > 2 && offset > 2) 1 else 0) + (if (d.length > 4 && offset > 4) 1 else 0)
            override fun transformedToOriginal(offset: Int) =
                (offset - (if (d.length > 2 && offset > 2) 1 else 0) - (if (d.length > 4 && offset > 5) 1 else 0)).coerceIn(0, d.length)
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out), map)
    }
}

// ---------------------------------------------------------------- price alerts

data class PriceAlertsUiState(
    val loading: Boolean = true,
    val items: List<PriceAlert> = emptyList(),
    val places: Map<String, Location> = emptyMap(),
    val error: AppError? = null,
)

@HiltViewModel
class PriceAlertsViewModel @Inject constructor(
    private val repo: PriceAlertRepository,
    private val locations: LocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PriceAlertsUiState())
    val state: StateFlow<PriceAlertsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
            try {
                val items = repo.list()
                val places = items.flatMap { listOf(it.fromId, it.toId) }.toSet().mapNotNull { id -> locations.getById(id)?.let { id to it } }.toMap()
                _state.update { it.copy(loading = false, items = items, places = places) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    fun delete(id: String) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
        viewModelScope.launch { runCatching { repo.delete(id) } }
    }
}

@Composable
internal fun PriceAlertsRoute(onBack: () -> Unit, viewModel: PriceAlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PriceAlertsScreen(state, onBack, viewModel::load, viewModel::delete)
}

/** Routes being watched for a lower fare. Stateless. */
@Composable
fun PriceAlertsScreen(state: PriceAlertsUiState, onBack: () -> Unit = {}, onRetry: () -> Unit = {}, onDelete: (String) -> Unit = {}) {
    val lang = JatriTheme.language
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = stringResource(R.string.alerts_title), onBack = onBack) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(onRetry = onRetry, message = state.error.text(), modifier = Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                title = stringResource(R.string.alerts_empty),
                message = stringResource(R.string.alerts_empty_body),
                icon = JatriIcons.NotificationsOutlined,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.items, key = { it.id }) { a ->
                    fun name(id: String) = state.places[id]?.city(lang) ?: id.substringAfter('_')
                    JatriCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(a.mode.icon, contentDescription = null, tint = a.mode.color)
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text("${name(a.fromId)} → ${name(a.toId)}", style = MaterialTheme.typography.titleMedium)
                                Text(DateTimeFormats.shortDate(a.date, lang), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
                            }
                            IconButton(onClick = { onDelete(a.id) }) {
                                Icon(JatriIcons.Close, contentDescription = stringResource(R.string.alerts_delete), tint = JatriTheme.colors.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            StatusChip(stringResource(R.string.alerts_target, a.target.format(lang)), StatusTone.Info)
                            val now = a.lastPrice
                            when {
                                !a.isActive -> StatusChip(stringResource(R.string.alerts_ended), StatusTone.Neutral)
                                now == null -> StatusChip(stringResource(R.string.alerts_not_on_sale), StatusTone.Neutral)
                                now <= a.target -> StatusChip(stringResource(R.string.alerts_now, now.format(lang)), StatusTone.Success)
                                else -> StatusChip(stringResource(R.string.alerts_now, now.format(lang)), StatusTone.Neutral)
                            }
                        }
                    }
                }
            }
        }
    }
}
