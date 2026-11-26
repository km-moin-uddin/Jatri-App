package com.jatri.feature.flight

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.FlightOffer
import com.jatri.core.ui.placeholder.PhasePlaceholderScreen
import com.jatri.core.ui.result.FlightResultCard
import com.jatri.core.ui.result.ResultsScaffold
import com.jatri.core.ui.result.routeSubtitle
import com.jatri.core.ui.result.routeTitle
import com.jatri.feature.flight.navigation.FlightResultsDestination

@Composable
internal fun FlightResultsRoute(
    onBack: () -> Unit,
    onOfferClick: (FlightOffer, FlightResultsDestination) -> Unit,
    viewModel: FlightResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultsScaffold(
        title = routeTitle(state.from, state.to),
        subtitle = routeSubtitle(state.date, state.passengers),
        loading = state.loading,
        count = state.offers.size,
        sort = state.sort,
        onSortChange = viewModel::setSort,
        onBack = onBack,
        phase = stringResource(R.string.flight_phase),
        date = state.date,
        onDateChange = viewModel::setDate,
    ) {
        items(state.sorted, key = { it.id }) { offer ->
            FlightResultCard(offer = offer, onClick = { onOfferClick(offer, state.args) })
        }
    }
}

@Composable
internal fun FlightDetailsRoute(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: FlightDetailsViewModel = hiltViewModel(),
) {
    val offer by viewModel.offer.collectAsStateWithLifecycle()
    PhasePlaceholderScreen(
        title = stringResource(R.string.flight_details_title),
        phase = stringResource(R.string.flight_phase),
        onBack = onBack,
        primaryActionText = stringResource(R.string.flight_continue),
        onPrimaryAction = onContinue,
    ) {
        offer?.let { FlightResultCard(offer = it, onClick = {}) }
        JatriCard {
            Text(stringResource(R.string.flight_fare_rules), style = MaterialTheme.typography.titleMedium)
            listOf(R.string.flight_rule_change, R.string.flight_rule_cancel, R.string.flight_rule_noshow).forEach {
                Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(JatriIcons.Document, contentDescription = null, tint = JatriTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
