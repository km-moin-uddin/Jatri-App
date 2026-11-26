package com.jatri.feature.flight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.seed.MockContent
import com.jatri.core.model.FlightOffer
import com.jatri.core.model.Location
import com.jatri.core.ui.result.ResultSort
import com.jatri.feature.flight.navigation.FlightDetailsDestination
import com.jatri.feature.flight.navigation.FlightResultsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class FlightResultsUiState(
    val args: FlightResultsDestination,
    val from: Location? = null,
    val to: Location? = null,
    val loading: Boolean = true,
    val offers: List<FlightOffer> = emptyList(),
    val sort: ResultSort = ResultSort.CHEAPEST,
) {
    val date: LocalDate get() = LocalDate.parse(args.date)
    val passengers: Int get() = args.adults + args.children + args.infants
    val sorted: List<FlightOffer>
        get() = when (sort) {
            ResultSort.CHEAPEST -> offers.sortedBy { it.price }
            ResultSort.FASTEST -> offers.sortedBy { it.duration }
            ResultSort.EARLIEST -> offers.sortedBy { it.departAt }
        }
}

/** MOCK: results come from [MockContent] until the Duffel sandbox adapter (Phase 5). */
@HiltViewModel
class FlightResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locations: LocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FlightResultsUiState(savedStateHandle.toRoute<FlightResultsDestination>()))
    val state: StateFlow<FlightResultsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val args = _state.value.args
            val from = locations.getById(args.fromId)
            val to = locations.getById(args.toId)
            _state.update { it.copy(from = from, to = to) }
            delay(MOCK_LATENCY_MS) // lets the shimmer show, like a real network call
            val offers = if (from != null && to != null) MockContent.flightOffers(args.date.let(LocalDate::parse), from, to) else emptyList()
            _state.update { it.copy(loading = false, offers = offers) }
        }
    }

    fun setSort(sort: ResultSort) = _state.update { it.copy(sort = sort) }
}

internal const val MOCK_LATENCY_MS = 700L

@HiltViewModel
class FlightDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locations: LocationRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<FlightDetailsDestination>()
    private val _offer = MutableStateFlow<FlightOffer?>(null)
    val offer: StateFlow<FlightOffer?> = _offer.asStateFlow()

    init {
        viewModelScope.launch {
            val from = locations.getById(args.fromId) ?: return@launch
            val to = locations.getById(args.toId) ?: return@launch
            _offer.value = MockContent.flightOffers(LocalDate.parse(args.date), from, to).firstOrNull { it.id == args.offerId }
        }
    }
}
