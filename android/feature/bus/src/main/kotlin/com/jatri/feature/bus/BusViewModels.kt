package com.jatri.feature.bus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.seed.MockContent
import com.jatri.core.model.BusTrip
import com.jatri.core.model.CoachType
import com.jatri.core.model.Location
import com.jatri.core.model.Money
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatLayout
import com.jatri.core.model.SeatState
import com.jatri.core.ui.result.ResultSort
import com.jatri.feature.bus.navigation.BusResultsDestination
import com.jatri.feature.bus.navigation.BusSeatsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

private const val MOCK_LATENCY_MS = 700L

data class BusResultsUiState(
    val args: BusResultsDestination,
    val from: Location? = null,
    val to: Location? = null,
    val loading: Boolean = true,
    val trips: List<BusTrip> = emptyList(),
    val sort: ResultSort = ResultSort.EARLIEST,
) {
    val date: LocalDate get() = LocalDate.parse(args.date)
    val sorted: List<BusTrip>
        get() = when (sort) {
            ResultSort.CHEAPEST -> trips.sortedBy { it.price }
            ResultSort.FASTEST -> trips.sortedBy { Duration.between(it.departAt, it.arriveAt) }
            ResultSort.EARLIEST -> trips.sortedBy { it.departAt }
        }
}

/** MOCK: trips come from [MockContent] until the bus provider adapter (Phase 3). */
@HiltViewModel
class BusResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locations: LocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BusResultsUiState(savedStateHandle.toRoute<BusResultsDestination>()))
    val state: StateFlow<BusResultsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val args = _state.value.args
            val from = locations.getById(args.fromId)
            val to = locations.getById(args.toId)
            _state.update { it.copy(from = from, to = to) }
            delay(MOCK_LATENCY_MS)
            val coach = runCatching { CoachType.valueOf(args.coach) }.getOrDefault(CoachType.ANY)
            val trips = if (from != null && to != null) {
                MockContent.busTrips(LocalDate.parse(args.date), from, to)
                    .filter { coach == CoachType.ANY || it.coachType == coach }
            } else {
                emptyList()
            }
            _state.update { it.copy(loading = false, trips = trips) }
        }
    }

    fun setSort(sort: ResultSort) = _state.update { it.copy(sort = sort) }
}

data class BusSeatsUiState(
    val trip: BusTrip? = null,
    val layout: SeatLayout = SeatLayout(emptyList()),
    val selected: Set<String> = emptySet(),
) {
    val total: Money get() = (trip?.price ?: Money.ZERO) * selected.size
}

@HiltViewModel
class BusSeatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locations: LocationRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<BusSeatsDestination>()
    private val _state = MutableStateFlow(BusSeatsUiState(layout = MockContent.busSeatLayout()))
    val state: StateFlow<BusSeatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val from = locations.getById(args.fromId) ?: return@launch
            val to = locations.getById(args.toId) ?: return@launch
            val trip = MockContent.busTrips(LocalDate.parse(args.date), from, to).firstOrNull { it.id == args.tripId }
            _state.update { it.copy(trip = trip) }
        }
    }

    /** Toggles a seat. Booked seats are ignored and at most 4 seats can be held per booking. */
    fun toggle(seat: Seat) = _state.update { s ->
        when {
            seat.state == SeatState.BOOKED -> s
            seat.number in s.selected -> s.copy(selected = s.selected - seat.number)
            s.selected.size >= SeatLayout.MAX_SEATS_PER_BOOKING -> s
            else -> s.copy(selected = s.selected + seat.number)
        }
    }
}
