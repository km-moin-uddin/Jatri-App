package com.jatri.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.OfflineBanner
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.component.ShimmerList
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Booking
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Location
import com.jatri.core.model.RefundStatus
import com.jatri.core.ui.color
import com.jatri.core.ui.icon
import com.jatri.core.ui.text

@Composable
internal fun TripsRoute(
    onSearchTickets: () -> Unit,
    onTicketClick: (String) -> Unit,
    onLogin: () -> Unit,
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refresh() // back from a ticket, payment or login
        onPauseOrDispose { }
    }
    TripsScreen(state, viewModel::selectTab, onTicketClick, onSearchTickets, onLogin, viewModel::refresh)
}

/** Upcoming / Completed / Cancelled, from the server or the phone's copy when offline. Stateless. */
@Composable
fun TripsScreen(
    state: TripsUiState,
    onTab: (TripsTab) -> Unit = {},
    onTicketClick: (String) -> Unit = {},
    onSearchTickets: () -> Unit = {},
    onLogin: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val lang = JatriTheme.language
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = stringResource(R.string.trips_title)) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OfflineBanner(visible = state.offline)
            if (!state.loggedIn) {
                EmptyState(
                    title = stringResource(R.string.trips_login_title),
                    message = stringResource(R.string.trips_login_body),
                    icon = JatriIcons.Ticket,
                    actionText = stringResource(R.string.trips_login),
                    onAction = onLogin,
                )
                return@Column
            }
            Row(
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TripsTab.entries.forEach { tab ->
                    val n = state.list(tab).size
                    val label = stringResource(
                        when (tab) {
                            TripsTab.UPCOMING -> R.string.trips_upcoming
                            TripsTab.COMPLETED -> R.string.trips_completed
                            TripsTab.CANCELLED -> R.string.trips_cancelled
                        },
                    )
                    PillChip(
                        text = if (n > 0 && !state.loading) "$label (${n.localized(lang)})" else label,
                        selected = tab == state.tab,
                        onClick = { onTab(tab) },
                    )
                }
            }
            val items = state.list(state.tab)
            when {
                state.loading -> ShimmerList()
                state.error != null -> ErrorState(onRetry = onRetry, message = state.error.text())
                items.isEmpty() -> EmptyState(
                    title = stringResource(
                        when (state.tab) {
                            TripsTab.UPCOMING -> R.string.trips_empty_upcoming
                            TripsTab.COMPLETED -> R.string.trips_empty_completed
                            TripsTab.CANCELLED -> R.string.trips_empty_cancelled
                        },
                    ),
                    message = if (state.tab == TripsTab.UPCOMING) stringResource(R.string.trips_empty_upcoming_body) else null,
                    icon = JatriIcons.Ticket,
                    actionText = if (state.tab == TripsTab.UPCOMING) stringResource(R.string.trips_search) else null,
                    onAction = if (state.tab == TripsTab.UPCOMING) onSearchTickets else null,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(items, key = { it.id }) { b -> TripCard(b, state.places) { onTicketClick(b.id) } }
                }
            }
        }
    }
}

/** "Dhaka → Cox's Bazar" (the first journey; "⇄" for a return trip). */
@Composable
fun tripTitle(b: Booking, places: Map<String, Location>): String {
    val lang = JatriTheme.language
    fun name(id: String) = places[id]?.city(lang) ?: id.substringAfter('_')
    val first = b.journeys.firstOrNull() ?: return ""
    val arrow = if (b.isFlight && b.journeys.size == 2 && b.journeys[1].last().toId == first.first().fromId) "⇄" else "→"
    return "${name(first.first().fromId)} $arrow ${name(first.last().toId)}"
}

@Composable
internal fun statusChip(b: Booking): Pair<String, StatusTone> = when (b.status) {
    BookingStatus.TICKETED, BookingStatus.CONFIRMED -> stringResource(R.string.trips_status_confirmed) to StatusTone.Success
    BookingStatus.HELD, BookingStatus.PENDING -> stringResource(R.string.trips_status_unpaid) to StatusTone.Warning
    BookingStatus.CANCELLED -> stringResource(R.string.trips_status_cancelled) to StatusTone.Danger
    BookingStatus.FAILED -> stringResource(R.string.trips_status_failed) to StatusTone.Danger
    BookingStatus.REFUNDED -> stringResource(R.string.trips_status_refunded) to StatusTone.Info
}

@Composable
private fun TripCard(b: Booking, places: Map<String, Location>, onClick: () -> Unit) {
    val lang = JatriTheme.language
    val seg = b.segments.firstOrNull()
    JatriCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(b.mode.color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(b.mode.icon, contentDescription = null, tint = b.mode.color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(tripTitle(b, places), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                b.departAt?.let {
                    Text(
                        DateTimeFormats.shortDate(it.toLocalDate(), lang) + " · " + DateTimeFormats.time(it, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = JatriTheme.colors.textSecondary,
                    )
                }
            }
            val (label, tone) = statusChip(b)
            StatusChip(label, tone)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(seg?.carrier, seg?.number).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(b.ref, style = MaterialTheme.typography.labelLarge, color = JatriTheme.colors.textSecondary)
        }
        b.refund?.let { r ->
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(
                    if (r.status == RefundStatus.REFUNDED) R.string.trips_refund_done else R.string.trips_refund_pending,
                    r.amount.format(lang),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (r.status == RefundStatus.REFUNDED) JatriTheme.colors.success else JatriTheme.colors.warning,
            )
        }
    }
}
