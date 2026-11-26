package com.jatri.feature.flight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.JatriBottomSheet
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.LoadingState
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.component.PriceSummaryBar
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppError
import com.jatri.core.model.FlightOffer
import com.jatri.core.model.Money
import com.jatri.core.model.TripType
import com.jatri.core.ui.flight.FareRulesCard
import com.jatri.core.ui.flight.JourneyDetails
import com.jatri.core.ui.flight.isReturnTrip
import com.jatri.core.ui.flight.journeyTitle
import com.jatri.core.ui.flight.label
import com.jatri.core.ui.label
import com.jatri.core.ui.result.FlightResultCard
import com.jatri.core.ui.result.ResultsScaffold
import com.jatri.core.ui.text
import kotlin.math.roundToLong

@Composable
internal fun FlightResultsRoute(
    onBack: () -> Unit,
    onOfferClick: (FlightOffer) -> Unit,
    viewModel: FlightResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FlightResultsScreen(
        state = state,
        onBack = onBack,
        onOfferClick = onOfferClick,
        onSortChange = viewModel::setSort,
        onDateChange = viewModel::setDate,
        onRetry = viewModel::retry,
        onFilterClick = viewModel::openFilters,
        onClearFilters = viewModel::clearFilters,
    )
    if (state.showFilters) {
        FlightFilterSheet(state = state, onApply = viewModel::applyFilters, onDismiss = viewModel::closeFilters)
    }
}

/** Stateless results screen, also used by screenshot tests. */
@Composable
fun FlightResultsScreen(
    state: FlightResultsUiState,
    onBack: () -> Unit = {},
    onOfferClick: (FlightOffer) -> Unit = {},
    onSortChange: (com.jatri.core.ui.result.ResultSort) -> Unit = {},
    onDateChange: (java.time.LocalDate) -> Unit = {},
    onRetry: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onClearFilters: () -> Unit = {},
) {
    val lang = JatriTheme.language
    val visible = state.visible
    ResultsScaffold(
        title = flightTitle(state),
        subtitle = flightSubtitle(state),
        loading = state.loading,
        count = visible.size,
        sort = state.sort,
        onSortChange = onSortChange,
        onBack = onBack,
        phase = null,
        date = state.date,
        onDateChange = onDateChange,
        error = state.error,
        onRetry = onRetry,
        showDates = state.tripType != TripType.MULTI_CITY,
        onFilterClick = onFilterClick.takeIf { state.offers.isNotEmpty() },
        activeFilters = state.filters.activeCount,
        onClearFilters = onClearFilters.takeIf { state.offers.isNotEmpty() && visible.isEmpty() },
    ) {
        items(visible, key = { it.id }) { offer ->
            FlightResultCard(offer = offer, onClick = { onOfferClick(offer) })
        }
        if (state.filters.activeCount > 0 && visible.size < state.offers.size) {
            item {
                Text(
                    stringResource(R.string.flight_hidden_by_filters, (state.offers.size - visible.size).localized(lang)),
                    style = MaterialTheme.typography.bodySmall,
                    color = JatriTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** "Dhaka → Cox's Bazar", "Dhaka ⇄ Dubai" or "Dhaka → Cox's Bazar → Chattogram". */
@Composable
private fun flightTitle(state: FlightResultsUiState): String {
    val lang = JatriTheme.language
    val places = state.places
    if (places.isEmpty()) return ""
    fun name(p: com.jatri.core.model.Location?) = p?.city(lang).orEmpty()
    return when (state.tripType) {
        TripType.ROUND_TRIP -> "${name(places.first().first)} ⇄ ${name(places.first().second)}"
        TripType.ONE_WAY -> "${name(places.first().first)} → ${name(places.first().second)}"
        TripType.MULTI_CITY -> (listOf(places.first().first) + places.map { it.second }).joinToString(" → ") { name(it) }
    }
}

@Composable
private fun flightSubtitle(state: FlightResultsUiState): String {
    val lang = JatriTheme.language
    val dates = when (state.tripType) {
        TripType.MULTI_CITY -> state.args.searchLegs.joinToString(", ") { DateTimeFormats.shortDate(it.date, lang) }
        else -> listOfNotNull(state.date, state.returnDate).joinToString(" – ") { DateTimeFormats.shortDate(it, lang) }
    }
    val pax = pluralStringResource(com.jatri.core.ui.R.plurals.ui_passenger_count, state.passengers, state.passengers.localized(lang))
    return "$dates · $pax · ${state.cabin.label()}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlightFilterSheet(state: FlightResultsUiState, onApply: (FlightFilters) -> Unit, onDismiss: () -> Unit) {
    val lang = JatriTheme.language
    var draft by remember { mutableStateOf(state.filters) }
    JatriBottomSheet(title = stringResource(R.string.flight_filters), onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.priceRange?.let { range ->
                val max = draft.maxPrice?.paisa ?: range.endInclusive
                Text(
                    stringResource(R.string.flight_filter_max_price, Money(max).format(lang)),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (range.endInclusive > range.start) {
                    Slider(
                        value = max.toFloat(),
                        onValueChange = { v ->
                            val rounded = (v / 10_000f).roundToLong() * 10_000 // whole ৳100 steps
                            draft = draft.copy(maxPrice = Money(rounded.coerceIn(range.start, range.endInclusive)).takeIf { it.paisa < range.endInclusive })
                        },
                        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
                    )
                }
            }
            Text(stringResource(R.string.flight_filter_stops), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(null to R.string.flight_filter_any, 0 to R.string.flight_filter_nonstop, 1 to R.string.flight_filter_one_stop).forEach { (v, res) ->
                    PillChip(stringResource(res), selected = draft.maxStops == v, onClick = { draft = draft.copy(maxStops = v) })
                }
            }
            Text(stringResource(R.string.flight_filter_departure), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DepartSlot.entries.forEach { slot ->
                    PillChip(
                        text = stringResource(
                            when (slot) {
                                DepartSlot.EARLY -> R.string.flight_slot_early
                                DepartSlot.MORNING -> R.string.flight_slot_morning
                                DepartSlot.AFTERNOON -> R.string.flight_slot_afternoon
                                DepartSlot.EVENING -> R.string.flight_slot_evening
                            },
                        ),
                        selected = slot in draft.slots,
                        onClick = { draft = draft.copy(slots = if (slot in draft.slots) draft.slots - slot else draft.slots + slot) },
                    )
                }
            }
            if (state.allAirlines.size > 1) {
                Text(stringResource(R.string.flight_filter_airlines), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    state.allAirlines.forEach { a ->
                        PillChip(
                            text = a,
                            selected = a in draft.airlines,
                            onClick = { draft = draft.copy(airlines = if (a in draft.airlines) draft.airlines - a else draft.airlines + a) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.flight_filter_refundable), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = draft.refundableOnly, onCheckedChange = { draft = draft.copy(refundableOnly = it) })
            }
            val count = state.offers.count(draft::matches)
            JatriCtaButton(text = stringResource(R.string.flight_filter_show, count.localized(lang)), onClick = { onApply(draft) })
            androidx.compose.material3.TextButton(onClick = { draft = FlightFilters() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(com.jatri.core.ui.R.string.ui_clear_filters))
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
internal fun FlightDetailsRoute(
    onBack: () -> Unit,
    onContinue: (offerId: String) -> Unit,
    viewModel: FlightDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FlightDetailsScreen(state = state, onBack = onBack, onContinue = onContinue, onRetry = viewModel::retry)
}

/** Itinerary, fare rules and the price per passenger type, priced live. Stateless. */
@Composable
fun FlightDetailsScreen(
    state: FlightDetailsUiState,
    onBack: () -> Unit = {},
    onContinue: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val lang = JatriTheme.language
    val offer = state.offer
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = stringResource(R.string.flight_details_title), onBack = onBack) },
        bottomBar = {
            if (offer != null) {
                PriceSummaryBar(
                    label = offer.fares.sumOf { it.count }.let { n ->
                        stringResource(R.string.flight_total) + " · " +
                            pluralStringResource(com.jatri.core.ui.R.plurals.ui_passenger_count, n, n.localized(lang))
                    },
                    price = offer.price.format(lang),
                    buttonText = stringResource(R.string.flight_continue),
                    onClick = { onContinue(offer.id) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            offer == null -> {
                val expired = (state.error as? AppError.Api)?.code == "offer_expired"
                ErrorState(
                    onRetry = if (expired) onBack else onRetry,
                    message = state.error?.text(),
                    modifier = Modifier.padding(padding),
                )
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.priceChangedFrom?.let { old ->
                    StatusChip(
                        stringResource(R.string.flight_price_updated, old.format(lang), offer.price.format(lang)),
                        StatusTone.Warning,
                    )
                }
                val returnTrip = isReturnTrip(offer.journeys.map { it.from.id }, offer.journeys.map { it.to.id })
                offer.journeys.forEachIndexed { i, j -> JourneyDetails(title = journeyTitle(i, returnTrip), journey = j) }
                FareRulesCard(rules = offer.rules, brand = offer.fareBrand, cabin = offer.cabinClass)
                PriceCard(offer)
                if (offer.international) {
                    Text(stringResource(R.string.flight_passport_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun PriceCard(offer: FlightOffer) {
    val lang = JatriTheme.language
    JatriCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.flight_price_breakdown), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        offer.fares.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${f.type.label()} × ${f.count.localized(lang)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text((f.base + f.tax).times(f.count).format(lang), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.flight_base_tax, f.base.format(lang), f.tax.format(lang)),
                style = MaterialTheme.typography.bodySmall,
                color = JatriTheme.colors.textSecondary,
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(stringResource(R.string.flight_service_charge), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(offer.serviceCharge.format(lang), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = JatriTheme.colors.divider)
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.flight_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(offer.price.format(lang), style = MaterialTheme.typography.titleMedium)
        }
    }
}
