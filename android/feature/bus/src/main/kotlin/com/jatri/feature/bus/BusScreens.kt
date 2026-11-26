package com.jatri.feature.bus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.format
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.PriceSummaryBar
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.BusTrip
import com.jatri.core.ui.placeholder.PhaseBanner
import com.jatri.core.ui.result.BusResultCard
import com.jatri.core.ui.result.ResultsScaffold
import com.jatri.core.ui.result.routeSubtitle
import com.jatri.core.ui.result.routeTitle
import com.jatri.core.ui.seat.SeatMap
import com.jatri.feature.bus.navigation.BusResultsDestination

@Composable
internal fun BusResultsRoute(
    onBack: () -> Unit,
    onTripClick: (BusTrip, BusResultsDestination) -> Unit,
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
        phase = stringResource(R.string.bus_phase),
    ) {
        items(state.sorted, key = { it.id }) { trip -> BusResultCard(trip, onClick = { onTripClick(trip, state.args) }) }
    }
}

@Composable
internal fun BusSeatsRoute(onBack: () -> Unit, onContinue: () -> Unit, viewModel: BusSeatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lang = JatriTheme.language
    Scaffold(
        topBar = {
            JatriTopAppBar(
                title = stringResource(R.string.bus_seats_title),
                subtitle = state.trip?.let { "${it.operatorName} · ${it.coachNumber}" },
                onBack = onBack,
            )
        },
        bottomBar = {
            PriceSummaryBar(
                label = if (state.selected.isEmpty()) {
                    stringResource(R.string.bus_no_seat_selected)
                } else {
                    stringResource(R.string.bus_selected_seats, state.selected.sorted().joinToString(", "))
                },
                price = state.total.format(lang),
                buttonText = stringResource(R.string.bus_continue),
                onClick = onContinue,
                enabled = state.selected.isNotEmpty(),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PhaseBanner(stringResource(R.string.bus_phase))
            SeatMap(
                layout = state.layout,
                selected = state.selected,
                onSeatToggle = viewModel::toggle,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.bus_boarding_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
        }
    }
}
