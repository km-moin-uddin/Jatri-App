package com.jatri.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.BookingRepository
import com.jatri.core.data.repository.BusRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.BusHoldRequest
import com.jatri.core.model.BusSeatMap
import com.jatri.core.model.Gender
import com.jatri.core.model.Money
import com.jatri.core.model.PassengerInput
import com.jatri.core.model.SeatState
import com.jatri.core.model.SessionState
import com.jatri.feature.booking.navigation.PassengerDetailsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import com.jatri.core.data.repository.TravellerRepository
import com.jatri.core.model.SavedTraveller
import javax.inject.Inject

data class PassengerForm(
    val seatNo: String,
    val ladiesSeat: Boolean,
    val name: String = "",
    val gender: Gender? = null,
    val age: String = "",
    val nid: String = "",
    val nameError: Boolean = false,
    val genderError: Boolean = false,
    val ageError: Boolean = false,
    val nidError: Boolean = false,
)

data class BusPassengerUiState(
    val loading: Boolean = true,
    val seatMap: BusSeatMap? = null,
    val passengers: List<PassengerForm> = emptyList(),
    val phone: String = "",
    val email: String = "",
    val phoneError: Boolean = false,
    val emailError: Boolean = false,
    val submitting: Boolean = false,
    val error: AppError? = null,
    /** The server says seats or price changed: the user must go back to the seat map. */
    val mustReselect: Boolean = false,
    val travellers: List<SavedTraveller> = emptyList(),
) {
    val total: Money
        get() = seatMap?.let { (it.trip.price + it.serviceChargePerSeat) * passengers.size } ?: Money.ZERO
}

@HiltViewModel
class BusPassengerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val buses: BusRepository,
    private val bookings: BookingRepository,
    auth: AuthRepository,
    private val travellers: TravellerRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<PassengerDetailsDestination>()
    private val seats = args.seats.split(',').filter(String::isNotBlank)

    /** One key per attempt, kept across retries after a timeout so they can't create a second hold. */
    private val idempotencyKey: String
        get() = savedStateHandle.get<String>(KEY) ?: UUID.randomUUID().toString().also { savedStateHandle[KEY] = it }

    private val _state = MutableStateFlow(
        BusPassengerUiState(
            phone = (auth.session.value as? SessionState.LoggedIn)?.user?.phone.orEmpty(),
            email = (auth.session.value as? SessionState.LoggedIn)?.user?.email.orEmpty(),
        ),
    )
    val state: StateFlow<BusPassengerUiState> = _state.asStateFlow()

    init {
        loadTravellers()
        val userName = (auth.session.value as? SessionState.LoggedIn)?.user?.name.orEmpty()
        viewModelScope.launch {
            try {
                val map = buses.seatMap(args.tripId)
                _state.update { s ->
                    s.copy(
                        loading = false,
                        seatMap = map,
                        passengers = seats.mapIndexed { i, seat ->
                            // Pre-fill the first passenger with the account holder's name.
                            PassengerForm(seat, map.layout.find(seat)?.state == SeatState.LADIES, name = if (i == 0) userName else "")
                        },
                    )
                }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    private fun loadTravellers() {
        viewModelScope.launch {
            val list = runCatching { travellers.list() }.getOrDefault(emptyList())
            _state.update { it.copy(travellers = list) }
        }
    }

    fun update(index: Int, transform: (PassengerForm) -> PassengerForm) = _state.update { s ->
        s.copy(passengers = s.passengers.mapIndexed { i, p -> if (i == index) transform(p) else p }, error = null)
    }

    fun setPhone(value: String) = _state.update { it.copy(phone = value.filter(Char::isDigit).take(11), phoneError = false) }
    fun setEmail(value: String) = _state.update { it.copy(email = value.trim(), emailError = false) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun submit(onHeld: (String) -> Unit) {
        val s = _state.value
        val map = s.seatMap ?: return
        if (s.submitting) return
        val checked = s.passengers.map { p ->
            val age = p.age.toIntOrNull()
            p.copy(
                nameError = p.name.trim().length < 2,
                genderError = p.gender == null || (p.ladiesSeat && p.gender != Gender.FEMALE),
                ageError = p.age.isNotEmpty() && (age == null || age !in 1..120),
                nidError = p.nid.isNotEmpty() && p.nid.length !in setOf(10, 13, 17),
            )
        }
        val phoneError = !Regex("^01[3-9]\\d{8}$").matches(s.phone)
        val emailError = s.email.isNotEmpty() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(s.email)
        val invalid = phoneError || emailError || checked.any { it.nameError || it.genderError || it.ageError || it.nidError }
        _state.update { it.copy(passengers = checked, phoneError = phoneError, emailError = emailError) }
        if (invalid) return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                val booking = bookings.createBusHold(
                    BusHoldRequest(
                        tripId = args.tripId,
                        seats = seats,
                        boardingPointId = args.boardingPointId,
                        droppingPointId = args.droppingPointId,
                        passengers = checked.map {
                            PassengerInput(it.name.trim(), it.gender!!, it.age.toIntOrNull(), it.seatNo, it.nid.ifBlank { null })
                        },
                        contactPhone = s.phone,
                        contactEmail = s.email.ifBlank { null },
                        expectedTotal = s.total,
                        idempotencyKey = idempotencyKey,
                    ),
                )
                _state.update { it.copy(submitting = false) }
                onHeld(booking.id)
            } catch (e: AppException) {
                val code = (e.error as? AppError.Api)?.code
                _state.update {
                    it.copy(submitting = false, error = e.error, mustReselect = code in setOf("seat_unavailable", "price_changed", "trip_not_found"))
                }
                // A changed request needs a new key; a network retry must reuse the old one.
                if (e.error is AppError.Api) savedStateHandle[KEY] = UUID.randomUUID().toString()
            }
        }
    }

    private companion object {
        const val KEY = "idempotency_key"
    }
}
