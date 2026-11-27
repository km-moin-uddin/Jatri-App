package com.jatri.feature.flight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.FlightRepository
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.CabinClass
import com.jatri.core.model.FlightOffer
import com.jatri.core.model.Location
import com.jatri.core.model.Money
import com.jatri.core.model.TripType
import com.jatri.core.ui.result.ResultSort
import com.jatri.feature.flight.navigation.FlightDetailsDestination
import com.jatri.feature.flight.navigation.FlightResultsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import com.jatri.core.model.PassengerType
import com.jatri.core.model.TravelMode
import com.jatri.core.data.repository.PriceAlertRepository
import com.jatri.core.ui.result.PriceAlertStatus

/** When the first flight takes off (local time). */
enum class DepartSlot(val hours: IntRange) { EARLY(0..5), MORNING(6..11), AFTERNOON(12..17), EVENING(18..23) }

/** Everything the filter sheet can narrow the list by. Defaults = no filter. */
data class FlightFilters(
    val maxPrice: Money? = null,
    val airlines: Set<String> = emptySet(),
    /** null = any, 0 = nonstop only, 1 = up to one stop. */
    val maxStops: Int? = null,
    val slots: Set<DepartSlot> = emptySet(),
    val refundableOnly: Boolean = false,
) {
    val activeCount: Int
        get() = listOf(maxPrice != null, airlines.isNotEmpty(), maxStops != null, slots.isNotEmpty(), refundableOnly).count { it }

    fun matches(offer: FlightOffer): Boolean =
        (maxPrice == null || offer.price <= maxPrice) &&
            (airlines.isEmpty() || offer.airlines.any { it in airlines }) &&
            (maxStops == null || offer.maxStops <= maxStops) &&
            (slots.isEmpty() || slots.any { offer.departAt.hour in it.hours }) &&
            (!refundableOnly || offer.rules.refundable)
}

data class FlightResultsUiState(
    val args: FlightResultsDestination,
    /** From/to of each journey, for the title. */
    val places: List<Pair<Location?, Location?>> = emptyList(),
    val loading: Boolean = true,
    val error: AppError? = null,
    val offers: List<FlightOffer> = emptyList(),
    val sort: ResultSort = ResultSort.CHEAPEST,
    val filters: FlightFilters = FlightFilters(),
    val showFilters: Boolean = false,
    val alert: PriceAlertStatus = PriceAlertStatus.IDLE,
    /** One-shot: true = alert saved, false = it failed. */
    val alertResult: Boolean? = null,
) {
    val tripType: TripType get() = runCatching { TripType.valueOf(args.tripType) }.getOrDefault(TripType.ONE_WAY)
    val date: LocalDate get() = args.searchLegs.first().date
    val returnDate: LocalDate? get() = args.searchLegs.getOrNull(1)?.date?.takeIf { tripType == TripType.ROUND_TRIP }
    val passengers: Int get() = args.adults + args.children + args.infants
    val cabin: CabinClass get() = runCatching { CabinClass.valueOf(args.cabin) }.getOrDefault(CabinClass.ECONOMY)

    val allAirlines: List<String> get() = offers.flatMap { it.airlines }.distinct().sorted()
    /** Lowest one-adult fare; alerts only for one-way economy (what the server checks). */
    val alertLowest: Money?
        get() = offers.takeIf { tripType == TripType.ONE_WAY && cabin == CabinClass.ECONOMY && !loading }
            ?.mapNotNull { o -> o.fares.firstOrNull { it.type == PassengerType.ADULT }?.let { it.base + it.tax + Money(o.serviceCharge.paisa / passengers.coerceAtLeast(1)) } }
            ?.minOrNull()
    val priceRange: ClosedRange<Long>? get() = offers.takeIf { it.isNotEmpty() }?.let { o -> o.minOf { it.price.paisa }..o.maxOf { it.price.paisa } }

    val visible: List<FlightOffer>
        get() = offers.filter(filters::matches).let { list ->
            when (sort) {
                ResultSort.CHEAPEST -> list.sortedWith(compareBy({ it.price }, { it.duration }))
                ResultSort.FASTEST -> list.sortedWith(compareBy({ it.duration }, { it.price }))
                ResultSort.EARLIEST -> list.sortedWith(compareBy({ it.departAt }, { it.price }))
            }
        }
}

@HiltViewModel
class FlightResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val flights: FlightRepository,
    private val locations: LocationRepository,
    private val alerts: PriceAlertRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FlightResultsUiState(savedStateHandle.toRoute<FlightResultsDestination>()))
    val state: StateFlow<FlightResultsUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    /** Date pill: move the trip to another day. A round trip keeps its length. */
    fun setDate(date: LocalDate) {
        val legs = _state.value.args.searchLegs
        val shift = ChronoUnit.DAYS.between(legs.first().date, date)
        val moved = legs.map { it.copy(date = it.date.plusDays(shift)) }
        _state.update { it.copy(args = it.args.withLegs(moved), offers = emptyList(), alert = PriceAlertStatus.IDLE) }
        load()
    }

    fun createAlert(target: Money) {
        val leg = _state.value.args.searchLegs.first()
        _state.update { it.copy(alert = PriceAlertStatus.SAVING) }
        viewModelScope.launch {
            val ok = runCatching { alerts.create(TravelMode.FLIGHT, leg.fromId, leg.toId, leg.date, target) }.isSuccess
            _state.update { it.copy(alert = if (ok) PriceAlertStatus.SAVED else PriceAlertStatus.IDLE, alertResult = ok) }
        }
    }

    fun alertShown() = _state.update { it.copy(alertResult = null) }

    fun retry() = load()

    fun setSort(sort: ResultSort) = _state.update { it.copy(sort = sort) }
    fun openFilters() = _state.update { it.copy(showFilters = true) }
    fun closeFilters() = _state.update { it.copy(showFilters = false) }
    fun applyFilters(filters: FlightFilters) = _state.update { it.copy(filters = filters, showFilters = false) }
    fun clearFilters() = _state.update { it.copy(filters = FlightFilters()) }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            val s = _state.value
            _state.update { it.copy(loading = true, error = null) }
            val legs = s.args.searchLegs
            val places = legs.map { locations.getById(it.fromId) to locations.getById(it.toId) }
            _state.update { it.copy(places = places) }
            try {
                val offers = flights.search(legs, s.args.adults, s.args.children, s.args.infants, s.cabin)
                _state.update { it.copy(loading = false, offers = offers) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }
}

data class FlightDetailsUiState(
    val loading: Boolean = true,
    val offer: FlightOffer? = null,
    val error: AppError? = null,
    /** The live price differs from the one on the results list. */
    val priceChangedFrom: Money? = null,
)

/** Always asks the server again: the price and seats shown before booking are live. */
@HiltViewModel
class FlightDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val flights: FlightRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<FlightDetailsDestination>()
    private val _state = MutableStateFlow(FlightDetailsUiState())
    val state: StateFlow<FlightDetailsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val offer = flights.offer(args.offerId)
                val changed = Money(args.shownPrice).takeIf { args.shownPrice > 0 && it != offer.price }
                _state.update { FlightDetailsUiState(loading = false, offer = offer, priceChangedFrom = changed) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }
}
