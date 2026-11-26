package com.jatri.feature.bus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.JatriBottomSheet
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.LoadingState
import com.jatri.core.designsystem.component.PriceSummaryBar
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.BusTrip
import com.jatri.core.model.StopPoint
import com.jatri.core.ui.result.BusResultCard
import com.jatri.core.ui.result.ResultsScaffold
import com.jatri.core.ui.result.routeSubtitle
import com.jatri.core.ui.result.routeTitle
import com.jatri.core.ui.seat.SeatMap
import com.jatri.core.ui.text
import com.jatri.feature.bus.navigation.BusSelection

@Composable
internal fun BusResultsRoute(
    onBack: () -> Unit,
    onTripClick: (BusTrip) -> Unit,
    viewModel: BusResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultsScaffold(
        title = routeTitle(state.from, state.to),
        subtitle = routeSubtitle(state.date, 1),
        loading = state.loading,
        count = state.trips.size,
        sort = state.sort,
        onSortChange = viewModel::setSort,
        onBack = onBack,
        // The bus module is real from Phase 3; only the provider behind it is a mock.
        phase = null,
        date = state.date,
        onDateChange = viewModel::setDate,
        error = state.error,
        onRetry = viewModel::retry,
    ) {
        items(state.sorted, key = { it.id }) { trip -> BusResultCard(trip, onClick = { onTripClick(trip) }) }
    }
}

private enum class PointSheet { NONE, BOARDING, DROPPING }

@Composable
internal fun BusSeatsRoute(
    onBack: () -> Unit,
    onContinue: (BusSelection) -> Unit,
    viewModel: BusSeatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lang = JatriTheme.language
    var sheet by rememberSaveable { mutableStateOf(PointSheet.NONE) }
    val trip = state.trip
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            JatriTopAppBar(
                title = stringResource(R.string.bus_seats_title),
                subtitle = trip?.let { "${it.operatorName} · ${it.coachNumber}" },
                onBack = onBack,
            )
        },
        bottomBar = {
            if (state.seatMap != null) {
                PriceSummaryBar(
                    label = if (state.selected.isEmpty()) {
                        stringResource(R.string.bus_no_seat_selected)
                    } else {
                        stringResource(R.string.bus_selected_seats, state.selected.sorted().joinToString(", "))
                    },
                    price = state.total.format(lang),
                    buttonText = stringResource(R.string.bus_continue),
                    onClick = { viewModel.selection()?.let(onContinue) },
                    enabled = state.canContinue,
                )
            }
        },
    ) { padding ->
        val map = state.seatMap
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            map == null -> ErrorState(onRetry = viewModel::retry, message = state.error?.text(), modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PointRow(
                    label = stringResource(R.string.bus_boarding_point),
                    point = map.trip.boardingPoints.firstOrNull { it.id == state.boardingId },
                    onClick = { sheet = PointSheet.BOARDING },
                )
                PointRow(
                    label = stringResource(R.string.bus_dropping_point),
                    point = map.trip.droppingPoints.firstOrNull { it.id == state.droppingId },
                    onClick = { sheet = PointSheet.DROPPING },
                )
                SeatMap(
                    layout = map.layout,
                    selected = state.selected,
                    onSeatToggle = viewModel::toggle,
                    maxSelectable = map.maxSelectable,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.bus_seat_limit, map.maxSelectable.localized(lang)) + " " + stringResource(R.string.bus_ladies_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.limitHit) JatriTheme.colors.warning else JatriTheme.colors.textSecondary,
                )
                Spacer(Modifier.heightIn(min = Spacing.md))
            }
        }
    }
    val map = state.seatMap
    if (map != null && sheet != PointSheet.NONE) {
        val boarding = sheet == PointSheet.BOARDING
        PointSheet(
            title = stringResource(if (boarding) R.string.bus_choose_boarding else R.string.bus_choose_dropping),
            points = if (boarding) map.trip.boardingPoints else map.trip.droppingPoints,
            selectedId = if (boarding) state.boardingId else state.droppingId,
            onSelect = {
                if (boarding) viewModel.setBoarding(it) else viewModel.setDropping(it)
                sheet = PointSheet.NONE
            },
            onDismiss = { sheet = PointSheet.NONE },
        )
    }
}

@Composable
private fun PointRow(label: String, point: StopPoint?, onClick: () -> Unit) {
    val lang = JatriTheme.language
    JatriCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(JatriIcons.Location, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
                Text(point?.name(lang).orEmpty(), style = MaterialTheme.typography.titleMedium)
            }
            point?.let { Text(DateTimeFormats.time(it.time, lang), style = MaterialTheme.typography.titleMedium) }
            Icon(JatriIcons.ChevronRight, contentDescription = null, tint = JatriTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun PointSheet(title: String, points: List<StopPoint>, selectedId: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val lang = JatriTheme.language
    JatriBottomSheet(title = title, onDismiss = onDismiss) {
        Column(Modifier.selectableGroup().padding(bottom = Spacing.lg)) {
            points.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = p.id == selectedId, role = Role.RadioButton, onClick = { onSelect(p.id) })
                        .padding(horizontal = Spacing.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = p.id == selectedId, onClick = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(p.name(lang), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(DateTimeFormats.time(p.time, lang), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

