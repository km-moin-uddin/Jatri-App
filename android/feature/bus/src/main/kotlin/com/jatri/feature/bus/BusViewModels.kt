package com.jatri.feature.bus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.BusRepository
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.BusSeatMap
import com.jatri.core.model.BusTrip
import com.jatri.core.model.CoachType
import com.jatri.core.model.Location
import com.jatri.core.model.Money
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatState
import com.jatri.core.ui.result.ResultSort
import com.jatri.feature.bus.navigation.BusResultsDestination
import com.jatri.feature.bus.navigation.BusSeatsDestination
import com.jatri.feature.bus.navigation.BusSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

data class BusResultsUiState(
    val args: BusResultsDestination,
    val from: Location? = null,
    val to: Location? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
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

@HiltViewModel
class BusResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locations: LocationRepository,
    private val buses: BusRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BusResultsUiState(savedStateHandle.toRoute<BusResultsDestination>()))
    val state: StateFlow<BusResultsUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    /** Date pill tapped: search the same route on another day. */
    fun setDate(date: LocalDate) {
        _state.update { it.copy(args = it.args.copy(date = date.toString())) }
        load()
    }

    fun retry() = load()

    fun setSort(sort: ResultSort) = _state.update { it.copy(sort = sort) }

    private fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null, trips = emptyList()) }
        job = viewModelScope.launch {
            val args = _state.value.args
            val from = locations.getById(args.fromId)
            val to = locations.getById(args.toId)
            _state.update { it.copy(from = from, to = to) }
            if (from == null || to == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val coach = runCatching { CoachType.valueOf(args.coach) }.getOrDefault(CoachType.ANY)
            try {
                val trips = buses.search(from, to, LocalDate.parse(args.date), coach)
                _state.update { it.copy(loading = false, trips = trips) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }
}

data class BusSeatsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val seatMap: BusSeatMap? = null,
    val selected: Set<String> = emptySet(),
    val boardingId: String? = null,
    val droppingId: String? = null,
    val limitHit: Boolean = false,
) {
    val trip: BusTrip? get() = seatMap?.trip
    val total: Money
        get() = seatMap?.let { (it.trip.price + it.serviceChargePerSeat) * selected.size } ?: Money.ZERO
    val canContinue: Boolean get() = selected.isNotEmpty() && boardingId != null && droppingId != null
}

@HiltViewModel
class BusSeatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val buses: BusRepository,
) : ViewModel() {
    private val tripId = savedStateHandle.toRoute<BusSeatsDestination>().tripId
    private val _state = MutableStateFlow(BusSeatsUiState())
    val state: StateFlow<BusSeatsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /** Called when coming back from a failed hold: the map may have changed. */
    fun refresh() = load(keepSelection = true)

    private fun load(keepSelection: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.seatMap == null, error = null) }
            try {
                val map = buses.seatMap(tripId)
                _state.update { s ->
                    val stillFree = if (keepSelection) s.selected.filter { map.layout.find(it)?.state.isFree() }.toSet() else emptySet()
                    s.copy(
                        loading = false,
                        seatMap = map,
                        selected = stillFree,
                        boardingId = s.boardingId ?: map.trip.boardingPoints.firstOrNull()?.id,
                        droppingId = s.droppingId ?: map.trip.droppingPoints.firstOrNull()?.id,
                    )
                }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    /** Toggles a seat. Taken seats are ignored; at most [BusSeatMap.maxSelectable] per booking. */
    fun toggle(seat: Seat) = _state.update { s ->
        val max = s.seatMap?.maxSelectable ?: 4
        when {
            !seat.state.isFree() -> s
            seat.number in s.selected -> s.copy(selected = s.selected - seat.number, limitHit = false)
            s.selected.size >= max -> s.copy(limitHit = true)
            else -> s.copy(selected = s.selected + seat.number, limitHit = false)
        }
    }

    fun setBoarding(id: String) = _state.update { it.copy(boardingId = id) }
    fun setDropping(id: String) = _state.update { it.copy(droppingId = id) }

    fun selection(): BusSelection? {
        val s = _state.value
        if (!s.canContinue) return null
        return BusSelection(tripId, s.selected.sorted(), s.boardingId!!, s.droppingId!!)
    }
}

private fun SeatState?.isFree() = this == SeatState.AVAILABLE || this == SeatState.LADIES
