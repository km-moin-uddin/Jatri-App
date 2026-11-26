package com.jatri.feature.train

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.seed.MockContent
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.model.Location
import com.jatri.core.model.TrainClassFare
import com.jatri.core.model.TrainTrip
import com.jatri.core.ui.placeholder.PhasePlaceholderScreen
import com.jatri.core.ui.result.ResultSort
import com.jatri.core.ui.result.ResultsScaffold
import com.jatri.core.ui.result.TrainResultCard
import com.jatri.core.ui.result.routeSubtitle
import com.jatri.core.ui.result.routeTitle
import com.jatri.feature.train.navigation.TrainResultsDestination
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

data class TrainResultsUiState(
    val args: TrainResultsDestination,
    val from: Location? = null,
    val to: Location? = null,
    val loading: Boolean = true,
    val trips: List<TrainTrip> = emptyList(),
    val sort: ResultSort = ResultSort.EARLIEST,
) {
    val date: LocalDate get() = LocalDate.parse(args.date)
    val sorted: List<TrainTrip>
        get() = when (sort) {
            ResultSort.CHEAPEST -> trips.sortedBy { t -> t.classes.minOf { it.price } }
            ResultSort.FASTEST -> trips.sortedBy { Duration.between(it.departAt, it.arriveAt) }
            ResultSort.EARLIEST -> trips.sortedBy { it.departAt }
        }
}

/**
 * MOCK: trains come from [MockContent]. Real data needs an authorised railway ticketing partner;
 * the backend TrainProvider adapter is built in Phase 6.
 */
@HiltViewModel
class TrainResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locations: LocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TrainResultsUiState(savedStateHandle.toRoute<TrainResultsDestination>()))
    val state: StateFlow<TrainResultsUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Date pill tapped: search the same route on another day. */
    fun setDate(date: LocalDate) {
        _state.update { it.copy(args = it.args.copy(date = date.toString()), loading = true, trips = emptyList()) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val args = _state.value.args
            val from = locations.getById(args.fromId)
            val to = locations.getById(args.toId)
            _state.update { it.copy(from = from, to = to) }
            delay(700)
            val trips = if (from != null && to != null) MockContent.trainTrips(LocalDate.parse(args.date), from, to) else emptyList()
            _state.update { if (it.args == args) it.copy(loading = false, trips = trips) else it }
        }
    }

    fun setSort(sort: ResultSort) = _state.update { it.copy(sort = sort) }
}

@Composable
internal fun TrainResultsRoute(
    onBack: () -> Unit,
    onClassClick: (TrainTrip, TrainClassFare) -> Unit,
    viewModel: TrainResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultsScaffold(
        title = routeTitle(state.from, state.to),
        subtitle = routeSubtitle(state.date, state.args.adults + state.args.children),
        loading = state.loading,
        count = state.trips.size,
        sort = state.sort,
        onSortChange = viewModel::setSort,
        onBack = onBack,
        phase = stringResource(R.string.train_phase),
        date = state.date,
        onDateChange = viewModel::setDate,
    ) {
        items(state.sorted, key = { it.id }) { trip -> TrainResultCard(trip, onClassClick = { onClassClick(trip, it) }) }
    }
}

@Composable
internal fun TrainSeatRoute(onBack: () -> Unit, onContinue: () -> Unit) {
    PhasePlaceholderScreen(
        title = stringResource(R.string.train_seat_title),
        phase = stringResource(R.string.train_phase),
        onBack = onBack,
        primaryActionText = stringResource(R.string.train_continue),
        onPrimaryAction = onContinue,
    ) {
        JatriCard { Text(stringResource(R.string.train_seat_note), style = MaterialTheme.typography.bodyMedium) }
    }
}
