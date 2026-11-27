package com.jatri.feature.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.reminder.TripReminders
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.BookingRepository
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.repository.PaymentRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.Booking
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.CancelQuote
import com.jatri.core.model.Location
import com.jatri.core.model.SessionState
import com.jatri.feature.trips.navigation.TicketDetailDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

enum class TripsTab { UPCOMING, COMPLETED, CANCELLED }

/**
 * Upcoming: tickets still ahead (and holds waiting for payment). Completed: tickets whose trip has
 * started. Cancelled: cancelled, refunded or failed bookings.
 */
fun tabOf(b: Booking, now: LocalDateTime): TripsTab? = when (b.status) {
    BookingStatus.TICKETED, BookingStatus.CONFIRMED -> if ((b.departAt ?: now).isAfter(now)) TripsTab.UPCOMING else TripsTab.COMPLETED
    BookingStatus.HELD, BookingStatus.PENDING -> if ((b.departAt ?: now).isAfter(now)) TripsTab.UPCOMING else null
    BookingStatus.CANCELLED, BookingStatus.REFUNDED, BookingStatus.FAILED -> TripsTab.CANCELLED
}

data class TripsUiState(
    val loggedIn: Boolean = true,
    val loading: Boolean = true,
    val bookings: List<Booking> = emptyList(),
    val places: Map<String, Location> = emptyMap(),
    /** Showing the copy saved on the phone because the server can't be reached. */
    val offline: Boolean = false,
    val error: AppError? = null,
    val tab: TripsTab = TripsTab.UPCOMING,
    val now: LocalDateTime = LocalDateTime.now(),
) {
    fun list(tab: TripsTab): List<Booking> {
        val items = bookings.filter { tabOf(it, now) == tab }
        // Upcoming soonest first; the others most recent first.
        return if (tab == TripsTab.UPCOMING) items.sortedBy { it.departAt } else items.sortedByDescending { it.departAt }
    }
}

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val bookings: BookingRepository,
    private val locations: LocationRepository,
    private val reminders: TripReminders,
    private val auth: AuthRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(TripsUiState())
    val state: StateFlow<TripsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: TripsTab) = _state.update { it.copy(tab = tab) }

    fun refresh() {
        viewModelScope.launch {
            val now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Dhaka")))
            if (auth.session.value !is SessionState.LoggedIn) {
                _state.update { it.copy(loggedIn = false, loading = false, bookings = emptyList(), now = now) }
                return@launch
            }
            _state.update { it.copy(loggedIn = true, loading = it.bookings.isEmpty(), error = null, now = now) }
            val (list, offline, error) = try {
                Triple(bookings.list(), false, null)
            } catch (e: AppException) {
                // No internet: show the tickets saved on the phone (QR codes work offline).
                val cached = bookings.cached()
                Triple(cached, e.error is AppError.Network, if (cached.isEmpty() || e.error !is AppError.Network) e.error else null)
            }
            if (!offline && error == null) reminders.sync(list)
            val ids = list.flatMap { b -> b.segments.flatMap { listOf(it.fromId, it.toId) } }.toSet()
            val places = ids.mapNotNull { id -> locations.getById(id)?.let { id to it } }.toMap()
            _state.update { it.copy(loading = false, bookings = list, places = places, offline = offline, error = error) }
        }
    }
}

data class CancelSheetState(
    val loading: Boolean = true,
    val quote: CancelQuote? = null,
    val cancelling: Boolean = false,
    val error: AppError? = null,
)

data class TicketDetailUiState(
    val loading: Boolean = true,
    val booking: Booking? = null,
    val places: Map<String, Location> = emptyMap(),
    val error: AppError? = null,
    val pdfBusy: Boolean = false,
    val cancel: CancelSheetState? = null,
    /** One-off message after an action (e.g. "Booking cancelled"). */
    val cancelledNow: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
) {
    val canCancel: Boolean
        get() = booking?.let { tabOf(it, now) == TripsTab.UPCOMING && it.status == BookingStatus.TICKETED } == true
}

@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookings: BookingRepository,
    private val payments: PaymentRepository,
    private val locations: LocationRepository,
    private val reminders: TripReminders,
    private val clock: Clock,
) : ViewModel() {
    private val bookingId = savedStateHandle.toRoute<TicketDetailDestination>().bookingId
    private val _state = MutableStateFlow(TicketDetailUiState())
    val state: StateFlow<TicketDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.booking == null, error = null, now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Dhaka")))) }
            try {
                show(bookings.get(bookingId))
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    private suspend fun show(b: Booking) {
        val ids = b.segments.flatMap { listOf(it.fromId, it.toId) }.toSet()
        val places = ids.mapNotNull { id -> locations.getById(id)?.let { id to it } }.toMap()
        _state.update { it.copy(loading = false, booking = b, places = places) }
    }

    fun pdf(onReady: (File) -> Unit) {
        if (_state.value.pdfBusy) return
        viewModelScope.launch {
            _state.update { it.copy(pdfBusy = true, error = null) }
            try {
                onReady(payments.ticketPdf(bookingId))
            } catch (e: AppException) {
                _state.update { it.copy(error = e.error) }
            } finally {
                _state.update { it.copy(pdfBusy = false) }
            }
        }
    }

    /** Opens the cancel sheet and asks the server what would come back. */
    fun startCancel() {
        _state.update { it.copy(cancel = CancelSheetState()) }
        viewModelScope.launch {
            try {
                val q = bookings.cancelQuote(bookingId)
                _state.update { it.copy(cancel = it.cancel?.copy(loading = false, quote = q)) }
            } catch (e: AppException) {
                _state.update { it.copy(cancel = it.cancel?.copy(loading = false, error = e.error)) }
            }
        }
    }

    fun dismissCancel() = _state.update { it.copy(cancel = null) }

    fun confirmCancel() {
        val quote = _state.value.cancel?.quote ?: return
        if (_state.value.cancel?.cancelling == true) return
        _state.update { it.copy(cancel = it.cancel?.copy(cancelling = true, error = null)) }
        viewModelScope.launch {
            try {
                val b = bookings.cancel(bookingId, quote.refund)
                show(b)
                reminders.sync(bookings.cached())
                _state.update { it.copy(cancel = null, cancelledNow = true) }
            } catch (e: AppException) {
                val code = (e.error as? AppError.Api)?.code
                // The refund moved (time passed a policy step): show the new quote.
                val fresh = if (code == "refund_changed") runCatching { bookings.cancelQuote(bookingId) }.getOrNull() else null
                _state.update { it.copy(cancel = it.cancel?.copy(cancelling = false, error = e.error, quote = fresh ?: it.cancel.quote)) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(cancelledNow = false) }
}
