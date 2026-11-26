package com.jatri.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.component.TicketCard
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.ui.placeholder.PhasePlaceholderScreen

private const val SAMPLE_BOOKING_ID = "sample"

/**
 * Upcoming / Completed / Cancelled. Phase 1 shows one clearly-labelled sample ticket under
 * Upcoming so the ticket detail screen can be reached; real bookings arrive in Phase 7.
 */
@Composable
fun TripsScreen(onSearchTickets: () -> Unit, onTicketClick: (String) -> Unit, showSample: Boolean = true) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.trips_upcoming, R.string.trips_completed, R.string.trips_cancelled)
    Scaffold(topBar = { JatriTopAppBar(title = stringResource(R.string.trips_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
                }
            }
            when (tab) {
                0 -> if (showSample) {
                    Column(Modifier.padding(Spacing.screen)) {
                        SampleTicket(onClick = { onTicketClick(SAMPLE_BOOKING_ID) })
                    }
                } else {
                    EmptyState(
                        title = stringResource(R.string.trips_empty_upcoming),
                        message = stringResource(R.string.trips_empty_upcoming_body),
                        actionText = stringResource(R.string.trips_search),
                        onAction = onSearchTickets,
                    )
                }
                1 -> EmptyState(title = stringResource(R.string.trips_empty_completed), icon = JatriIcons.History)
                else -> EmptyState(title = stringResource(R.string.trips_empty_cancelled), icon = JatriIcons.Close)
            }
        }
    }
}

@Composable
private fun SampleTicket(onClick: (() -> Unit)?) {
    androidx.compose.material3.Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        TicketCard(
            modifier = Modifier.fillMaxWidth(),
            top = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StatusChip(stringResource(R.string.trips_sample), StatusTone.Info)
                    StatusChip(stringResource(R.string.trips_upcoming), StatusTone.Success)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.trips_sample_route), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.trips_sample_operator), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
            },
            bottom = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.trips_pnr), style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
                        Text("JT8K2Q", style = MaterialTheme.typography.titleLarge)
                    }
                    Icon(JatriIcons.QrCode, contentDescription = null, modifier = Modifier.size(56.dp))
                }
            },
        )
    }
}

@Composable
fun TicketDetailScreen(onBack: () -> Unit) {
    PhasePlaceholderScreen(
        title = stringResource(R.string.trips_detail_title),
        phase = stringResource(R.string.trips_phase),
        onBack = onBack,
    ) {
        SampleTicket(onClick = null)
        Text(stringResource(R.string.trips_actions_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
    }
}
