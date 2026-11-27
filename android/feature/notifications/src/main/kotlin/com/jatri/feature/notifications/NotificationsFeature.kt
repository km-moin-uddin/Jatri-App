package com.jatri.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.NotificationRepository
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.ShimmerList
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.AppNotification
import com.jatri.core.model.SessionState
import com.jatri.core.ui.text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class NotificationsUiState(
    val loading: Boolean = true,
    val loggedIn: Boolean = true,
    val items: List<AppNotification> = emptyList(),
    val error: AppError? = null,
) {
    val unread: Int get() = items.count { !it.isRead }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repo: NotificationRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (auth.session.value !is SessionState.LoggedIn) {
                _state.update { it.copy(loading = false, loggedIn = false) }
                return@launch
            }
            _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
            try {
                val items = repo.inbox()
                _state.update { it.copy(loading = false, items = items) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    /** Marks it read and hands back the booking to open, if any. */
    fun open(n: AppNotification, onBooking: (String) -> Unit) {
        if (!n.isRead) {
            _state.update { s -> s.copy(items = s.items.map { if (it.id == n.id) it.copy(isRead = true) else it }) }
            viewModelScope.launch { runCatching { repo.markRead(n.id) } }
        }
        n.bookingId?.let(onBooking)
    }

    fun markAllRead() {
        _state.update { s -> s.copy(items = s.items.map { it.copy(isRead = true) }) }
        viewModelScope.launch { runCatching { repo.markAllRead() } }
    }
}

@Composable
internal fun NotificationsRoute(onBack: () -> Unit, onOpenBooking: (String) -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpen = { viewModel.open(it, onOpenBooking) },
        onMarkAllRead = viewModel::markAllRead,
    )
}

/** Inbox: booking, payment, refund, reminder and flight-change messages, newest first. Stateless. */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpen: (AppNotification) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            JatriTopAppBar(
                title = stringResource(R.string.notif_title),
                onBack = onBack,
                actions = {
                    if (state.unread > 0) TextButton(onClick = onMarkAllRead) { Text(stringResource(R.string.notif_mark_all)) }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> ShimmerList(Modifier.padding(padding))
            state.error != null -> ErrorState(onRetry = onRetry, message = state.error.text(), modifier = Modifier.padding(padding))
            !state.loggedIn || state.items.isEmpty() -> EmptyState(
                title = stringResource(R.string.notif_empty),
                message = stringResource(R.string.notif_empty_body),
                icon = JatriIcons.Notifications,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.items, key = { it.id }) { n -> NotificationRow(n) { onOpen(n) } }
            }
        }
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    "booking_confirmed" -> JatriIcons.CheckCircle
    "payment_failed", "booking_failed" -> JatriIcons.Error
    "booking_cancelled" -> JatriIcons.Close
    "refund_processed" -> JatriIcons.Payments
    "journey_reminder" -> JatriIcons.Time
    "flight_changed" -> JatriIcons.Flight
    else -> JatriIcons.Notifications
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    val lang = JatriTheme.language
    val time = n.createdAt.atZone(ZoneId.of("Asia/Dhaka")).toLocalDateTime()
    val warn = n.type in setOf("payment_failed", "booking_failed", "flight_changed")
    JatriCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).background(
                    if (warn) JatriTheme.colors.warningContainer else JatriTheme.colors.successContainer,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(n.type), contentDescription = null, tint = if (warn) JatriTheme.colors.warning else JatriTheme.colors.success)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(n.title(lang), style = MaterialTheme.typography.titleSmall)
                Text(n.body(lang), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
                Text(
                    DateTimeFormats.shortDate(time.toLocalDate(), lang) + ", " + DateTimeFormats.time(time, lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = JatriTheme.colors.textSecondary,
                )
            }
            if (!n.isRead) {
                Box(Modifier.padding(top = 6.dp).size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}
