package com.jatri.feature.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.BookingRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.Booking
import com.jatri.core.model.BookingStatus
import com.jatri.feature.payment.navigation.ReviewPayDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

data class ReviewPayUiState(
    val loading: Boolean = false,
    val booking: Booking? = null,
    val secondsLeft: Int = 0,
    val expired: Boolean = false,
    val error: AppError? = null,
    val cancelling: Boolean = false,
)

@HiltViewModel
class ReviewPayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookings: BookingRepository,
    private val clock: Clock,
) : ViewModel() {
    val bookingId: String? = savedStateHandle.toRoute<ReviewPayDestination>().bookingId
    private val _state = MutableStateFlow(ReviewPayUiState(loading = bookingId != null))
    val state: StateFlow<ReviewPayUiState> = _state.asStateFlow()

    init {
        if (bookingId != null) load(bookingId)
    }

    fun retry() {
        bookingId?.let(::load)
    }

    private fun load(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val booking = bookings.get(id)
                _state.update { it.copy(loading = false, booking = booking, expired = booking.status != BookingStatus.HELD) }
                tick()
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    /** Counts down to the server's hold deadline (the server is the source of truth). */
    private suspend fun tick() {
        val deadline = _state.value.booking?.holdExpiresAt ?: return
        while (viewModelScope.isActive) {
            val left = Duration.between(clock.instant(), deadline).seconds.toInt().coerceAtLeast(0)
            _state.update { it.copy(secondsLeft = left, expired = it.expired || left == 0) }
            if (left == 0) {
                // Let the server release the seats now rather than waiting for its own expiry check.
                bookingId?.let { id -> runCatching { bookings.get(id) } }
                return
            }
            delay(1_000)
        }
    }

    fun cancel(onDone: () -> Unit) {
        val id = bookingId ?: return onDone()
        viewModelScope.launch {
            _state.update { it.copy(cancelling = true) }
            runCatching { bookings.cancel(id) }
            _state.update { it.copy(cancelling = false) }
            onDone()
        }
    }
}
