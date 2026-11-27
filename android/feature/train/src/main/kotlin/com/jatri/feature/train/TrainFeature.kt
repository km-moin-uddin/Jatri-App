package com.jatri.feature.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localized
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.repository.TrainRepository
import com.jatri.core.data.repository.PriceAlertRepository
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.LoadingState
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.component.PriceSummaryBar
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.Location
import com.jatri.core.model.Money
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatState
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TrainClassFare
import com.jatri.core.model.TrainSeatMap
import com.jatri.core.model.TrainTrip
import com.jatri.core.model.TravelMode
import com.jatri.core.ui.label
import com.jatri.core.ui.result.ResultSort
import com.jatri.core.ui.result.ResultsScaffold
import com.jatri.core.ui.result.PriceAlertAction
import com.jatri.core.ui.result.PriceAlertSnackbar
import com.jatri.core.ui.result.PriceAlertStatus
import com.jatri.core.ui.result.TrainResultCard
import com.jatri.core.ui.result.routeSubtitle
import com.jatri.core.ui.result.routeTitle
import com.jatri.core.ui.seat.SeatMap
import com.jatri.core.ui.text
import com.jatri.feature.train.navigation.TrainResultsDestination
import com.jatri.feature.train.navigation.TrainSeatDestination
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

/** Bangladesh Railway sells tickets this many days ahead. */
const val TRAIN_SALE_DAYS = 10L

data class TrainResultsUiState(
    val args: TrainResultsDestination,
    val from: Location? = null,
    val to: Location? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val trips: List<TrainTrip> = emptyList(),
    val sort: ResultSort = ResultSort.EARLIEST,
    val alert: PriceAlertStatus = PriceAlertStatus.IDLE,
    /** One-shot: true = alert saved, false = it failed. */
    val alertResult: Boolean? = null,
) {
    val date: LocalDate get() = LocalDate.parse(args.date)
    val lowest: Money? get() = trips.flatMap { it.classes }.minOfOrNull { it.price }
    val sorted: List<TrainTrip>
        get() = when (sort) {
            ResultSort.CHEAPEST -> trips.sortedBy { t -> t.classes.minOf { it.price } }
            ResultSort.FASTEST -> trips.sortedBy { Duration.between(it.departAt, it.arriveAt) }
            ResultSort.EARLIEST -> trips.sortedBy { it.departAt }
        }
}

@HiltViewModel
class TrainResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locations: LocationRepository,
    private val trains: TrainRepository,
    private val alerts: PriceAlertRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TrainResultsUiState(savedStateHandle.toRoute<TrainResultsDestination>()))
    val state: StateFlow<TrainResultsUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    /** Date pill tapped: search the same route on another day. */
    fun setDate(date: LocalDate) {
        _state.update { it.copy(args = it.args.copy(date = date.toString()), trips = emptyList(), alert = PriceAlertStatus.IDLE) }
        load()
    }

    fun retry() = load()

    fun setSort(sort: ResultSort) = _state.update { it.copy(sort = sort) }

    fun createAlert(target: Money) {
        val args = _state.value.args
        _state.update { it.copy(alert = PriceAlertStatus.SAVING) }
        viewModelScope.launch {
            val ok = runCatching { alerts.create(TravelMode.TRAIN, args.fromId, args.toId, LocalDate.parse(args.date), target) }.isSuccess
            _state.update { it.copy(alert = if (ok) PriceAlertStatus.SAVED else PriceAlertStatus.IDLE, alertResult = ok) }
        }
    }

    fun alertShown() = _state.update { it.copy(alertResult = null) }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            val args = _state.value.args
            _state.update { it.copy(loading = true, error = null) }
            val from = locations.getById(args.fromId)
            val to = locations.getById(args.toId)
            _state.update { it.copy(from = from, to = to) }
            if (from == null || to == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            try {
                // All classes: the card shows every class, and the chosen one stays visible.
                val trips = trains.search(from, to, LocalDate.parse(args.date), null)
                _state.update { it.copy(loading = false, trips = trips) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }
}

@Composable
internal fun TrainResultsRoute(
    onBack: () -> Unit,
    onClassClick: (TrainTrip, TrainClassFare) -> Unit,
    viewModel: TrainResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    PriceAlertSnackbar(state.alertResult, snackbar, viewModel::alertShown)
    TrainResultsScreen(
        state, onBack, onClassClick, viewModel::setSort, viewModel::setDate, viewModel::retry,
        onCreateAlert = viewModel::createAlert, snackbar = snackbar,
    )
}

/** Stateless results screen, also used by screenshot tests. */
@Composable
fun TrainResultsScreen(
    state: TrainResultsUiState,
    onBack: () -> Unit = {},
    onClassClick: (TrainTrip, TrainClassFare) -> Unit = { _, _ -> },
    onSortChange: (ResultSort) -> Unit = {},
    onDateChange: (LocalDate) -> Unit = {},
    onRetry: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    onCreateAlert: (Money) -> Unit = {},
    snackbar: SnackbarHostState? = null,
) {
    ResultsScaffold(
        title = routeTitle(state.from, state.to),
        subtitle = routeSubtitle(state.date, state.args.adults + state.args.children),
        actions = { PriceAlertAction(state.lowest.takeUnless { state.loading }, state.alert, onCreateAlert) },
        snackbarHost = snackbar,
        loading = state.loading,
        count = state.trips.size,
        sort = state.sort,
        onSortChange = onSortChange,
        onBack = onBack,
        phase = null,
        date = state.date,
        onDateChange = { if (!it.isAfter(today.plusDays(TRAIN_SALE_DAYS))) onDateChange(it) },
        today = today,
        error = state.error,
        onRetry = onRetry,
    ) {
        item {
            Text(
                stringResource(R.string.train_sale_note),
                style = MaterialTheme.typography.bodySmall,
                color = JatriTheme.colors.textSecondary,
            )
        }
        items(state.sorted, key = { it.id }) { trip -> TrainResultCard(trip, onClassClick = { onClassClick(trip, it) }) }
    }
}

// ---------------------------------------------------------------- coach & seat selection

data class TrainSeatsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val map: TrainSeatMap? = null,
    val coachIndex: Int = 0,
    val selected: List<String> = emptyList(),
) {
    val total: Money get() = map?.let { (it.fare + it.serviceChargePerSeat) * selected.size } ?: Money.ZERO
}

@HiltViewModel
class TrainSeatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trains: TrainRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<TrainSeatDestination>()
    val trainClass: TrainClass = TrainClass.valueOf(args.trainClass)
    private val _state = MutableStateFlow(TrainSeatsUiState())
    val state: StateFlow<TrainSeatsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /** Back from the passenger form after a seat was taken: fetch again, keep what's still free. */
    fun refresh() = load(keep = true)

    private fun load(keep: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = !keep || it.map == null, error = null) }
            try {
                val map = trains.seatMap(args.tripId, trainClass)
                val free = map.coaches.flatMap { c -> c.layout.rows.flatten().filterNotNull() }
                    .filter { it.state == SeatState.AVAILABLE }.map { it.number }.toSet()
                _state.update { s ->
                    s.copy(
                        loading = false,
                        map = map,
                        coachIndex = s.coachIndex.coerceAtMost(map.coaches.lastIndex.coerceAtLeast(0)),
                        selected = if (keep) s.selected.filter { it in free } else emptyList(),
                    )
                }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    fun selectCoach(index: Int) = _state.update { it.copy(coachIndex = index) }

    fun toggle(seat: Seat) = _state.update { s ->
        val max = s.map?.maxSelectable ?: 4
        when {
            seat.number in s.selected -> s.copy(selected = s.selected - seat.number)
            seat.state != SeatState.AVAILABLE || s.selected.size >= max -> s
            else -> s.copy(selected = s.selected + seat.number)
        }
    }

    val tripId: String get() = args.tripId
}

@Composable
internal fun TrainSeatRoute(
    onBack: () -> Unit,
    onContinue: (tripId: String, trainClass: TrainClass, seats: List<String>) -> Unit,
    viewModel: TrainSeatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrainSeatScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onCoach = viewModel::selectCoach,
        onToggle = viewModel::toggle,
        onContinue = { onContinue(viewModel.tripId, viewModel.trainClass, state.selected) },
    )
}

/** Coach pills on top, the chosen coach's seats, and a sticky total. Stateless. */
@Composable
fun TrainSeatScreen(
    state: TrainSeatsUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onCoach: (Int) -> Unit = {},
    onToggle: (Seat) -> Unit = {},
    onContinue: () -> Unit = {},
) {
    val lang = JatriTheme.language
    val map = state.map
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            JatriTopAppBar(
                title = map?.let { "${it.trip.trainName} (${it.trip.trainNumber})" } ?: stringResource(R.string.train_seat_title),
                subtitle = map?.let {
                    "${it.trainClass.label()} · ${DateTimeFormats.shortDate(it.trip.departAt.toLocalDate(), lang)}, " +
                        DateTimeFormats.time(it.trip.departAt, lang)
                },
                onBack = onBack,
            )
        },
        bottomBar = {
            if (map != null) {
                PriceSummaryBar(
                    label = if (state.selected.isEmpty()) {
                        stringResource(R.string.train_pick_seats, map.maxSelectable.localized(lang))
                    } else {
                        state.selected.joinToString(", ")
                    },
                    price = state.total.format(lang),
                    buttonText = stringResource(R.string.train_continue),
                    onClick = onContinue,
                    enabled = state.selected.isNotEmpty(),
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            map == null -> ErrorState(onRetry = onRetry, message = state.error?.text(), modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    map.coaches.forEachIndexed { i, c ->
                        val free = c.layout.rows.flatten().count { it?.state == SeatState.AVAILABLE }
                        PillChip(
                            text = stringResource(R.string.train_coach_free, c.name, free.localized(lang)),
                            selected = i == state.coachIndex,
                            onClick = { onCoach(i) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.train_fare_per_seat, map.fare.format(lang)),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (map.coaches.getOrNull(state.coachIndex)?.berth == true) {
                        StatusChip(stringResource(R.string.train_berth_note), StatusTone.Info)
                    }
                }
                val coach = map.coaches.getOrNull(state.coachIndex)
                if (coach != null) {
                    SeatMap(
                        layout = coach.layout,
                        selected = state.selected.toSet(),
                        onSeatToggle = onToggle,
                        maxSelectable = map.maxSelectable,
                        showDriver = false,
                        seatLabel = { it.number.substringAfter('-') },
                        showLadies = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(stringResource(R.string.train_nid_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}
