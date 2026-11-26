package com.jatri.feature.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.BookingRepository
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.repository.PaymentRepository
import com.jatri.core.data.repository.PaymentReturnEvents
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.Booking
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Location
import com.jatri.core.model.PaymentStatus
import com.jatri.feature.payment.navigation.PaymentProcessingDestination
import com.jatri.feature.payment.navigation.PaymentResultDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ProcessingPhase { DEMO_CHOICE, WAITING, SLOW, REFUND }

data class ProcessingUiState(
    val phase: ProcessingPhase = ProcessingPhase.WAITING,
    val amountPaisa: Long = 0,
)

/**
 * Polls `GET /payments/{id}/status` until the server has a verdict. The browser coming back
 * (deep link) triggers an immediate check; otherwise every 2 s for up to 3 minutes.
 */
@HiltViewModel
class PaymentProcessingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val payments: PaymentRepository,
    returns: PaymentReturnEvents,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<PaymentProcessingDestination>()
    private val _state = MutableStateFlow(
        ProcessingUiState(phase = if (args.demo) ProcessingPhase.DEMO_CHOICE else ProcessingPhase.WAITING, amountPaisa = args.amountPaisa),
    )
    val state: StateFlow<ProcessingUiState> = _state.asStateFlow()

    private var onTicketed: ((String) -> Unit)? = null
    private var onFailed: (() -> Unit)? = null
    private var polling: Job? = null
    private var finished = false

    init {
        viewModelScope.launch { returns.returns.collect { if (it == args.paymentId) checkNow() } }
    }

    fun bind(onTicketed: (String) -> Unit, onFailed: () -> Unit) {
        this.onTicketed = onTicketed
        this.onFailed = onFailed
        if (_state.value.phase == ProcessingPhase.WAITING && polling == null) startPolling()
    }

    fun demoAnswer(success: Boolean) {
        _state.update { it.copy(phase = ProcessingPhase.WAITING) }
        viewModelScope.launch {
            runCatching { payments.completeDemo(args.paymentId, success) }
            startPolling()
        }
    }

    fun checkAgain() {
        _state.update { it.copy(phase = ProcessingPhase.WAITING) }
        startPolling()
    }

    private fun checkNow() = viewModelScope.launch { check() }

    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch {
            repeat(MAX_POLLS) {
                if (check()) return@launch
                delay(POLL_MS)
            }
            _state.update { it.copy(phase = ProcessingPhase.SLOW) }
        }
    }

    /** @return true once there is a final answer. */
    private suspend fun check(): Boolean {
        if (finished) return true
        val s = try {
            payments.status(args.paymentId)
        } catch (e: AppException) {
            return false // offline for a moment: keep trying
        }
        when {
            s.status == PaymentStatus.SUCCESS && s.bookingStatus == BookingStatus.TICKETED -> {
                finished = true
                onTicketed?.invoke(s.bookingId)
            }
            s.status == PaymentStatus.REFUND_PENDING -> {
                finished = true
                _state.update { it.copy(phase = ProcessingPhase.REFUND) }
            }
            s.status == PaymentStatus.FAILED || s.status == PaymentStatus.CANCELLED -> {
                finished = true
                onFailed?.invoke()
            }
            else -> return false
        }
        return true
    }

    private companion object {
        const val POLL_MS = 2_000L
        const val MAX_POLLS = 90 // 3 minutes
    }
}

data class TicketUiState(
    val loading: Boolean = false,
    val booking: Booking? = null,
    val from: Location? = null,
    val to: Location? = null,
    val error: AppError? = null,
    val pdfBusy: Boolean = false,
)

@HiltViewModel
class TicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookings: BookingRepository,
    private val payments: PaymentRepository,
    private val locations: LocationRepository,
) : ViewModel() {
    private val bookingId = savedStateHandle.toRoute<PaymentResultDestination>().bookingId
    private val _state = MutableStateFlow(TicketUiState(loading = bookingId != null))
    val state: StateFlow<TicketUiState> = _state.asStateFlow()

    init {
        if (bookingId != null) {
            viewModelScope.launch {
                try {
                    val b = bookings.get(bookingId)
                    val seg = b.segments.firstOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            booking = b,
                            from = seg?.let { s -> locations.getById(s.fromId) },
                            to = seg?.let { s -> locations.getById(s.toId) },
                        )
                    }
                } catch (e: AppException) {
                    _state.update { it.copy(loading = false, error = e.error) }
                }
            }
        }
    }

    fun pdf(onReady: (File) -> Unit) {
        val id = bookingId ?: return
        if (_state.value.pdfBusy) return
        viewModelScope.launch {
            _state.update { it.copy(pdfBusy = true, error = null) }
            try {
                onReady(payments.ticketPdf(id))
            } catch (e: AppException) {
                _state.update { it.copy(error = e.error) }
            } finally {
                _state.update { it.copy(pdfBusy = false) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
