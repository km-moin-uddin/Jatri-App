package com.jatri.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.TrainRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.Money
import com.jatri.core.model.PassengerInput
import com.jatri.core.model.SessionState
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TrainHoldRequest
import com.jatri.core.model.TrainSeatMap
import com.jatri.feature.booking.navigation.PassengerDetailsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Bangladesh Railway: every passenger aged 18+ needs an NID. */
const val TRAIN_NID_AGE = 18

data class TrainPassengerUiState(
    val loading: Boolean = true,
    val seatMap: TrainSeatMap? = null,
    val passengers: List<PassengerForm> = emptyList(),
    val phone: String = "",
    val email: String = "",
    val phoneError: Boolean = false,
    val emailError: Boolean = false,
    val submitting: Boolean = false,
    val error: AppError? = null,
    val mustReselect: Boolean = false,
) {
    val total: Money get() = seatMap?.let { (it.fare + it.serviceChargePerSeat) * passengers.size } ?: Money.ZERO
}

@HiltViewModel
class TrainPassengerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val trains: TrainRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<PassengerDetailsDestination>()
    private val seats = args.seats.split(',').filter(String::isNotBlank)
    private val trainClass = TrainClass.valueOf(args.travelClass)
    private val user = (auth.session.value as? SessionState.LoggedIn)?.user

    private val idempotencyKey: String
        get() = savedStateHandle.get<String>(KEY) ?: UUID.randomUUID().toString().also { savedStateHandle[KEY] = it }

    private val _state = MutableStateFlow(TrainPassengerUiState(phone = user?.phone.orEmpty(), email = user?.email.orEmpty()))
    val state: StateFlow<TrainPassengerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val map = trains.seatMap(args.tripId, trainClass)
                _state.update { s ->
                    s.copy(
                        loading = false,
                        seatMap = map,
                        passengers = s.passengers.ifEmpty {
                            seats.mapIndexed { i, seat -> PassengerForm(seat, ladiesSeat = false, name = if (i == 0) user?.name.orEmpty() else "") }
                        },
                    )
                }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
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
        if (s.seatMap == null || s.submitting) return
        val checked = s.passengers.map { p ->
            val age = p.age.toIntOrNull()
            p.copy(
                nameError = p.name.trim().length < 2,
                genderError = p.gender == null,
                ageError = age == null || age !in 1..120,
                nidError = (p.nid.isNotEmpty() && p.nid.length !in setOf(10, 13, 17)) || ((age ?: 0) >= TRAIN_NID_AGE && p.nid.isEmpty()),
            )
        }
        val phoneError = !Regex("^01[3-9]\\d{8}$").matches(s.phone)
        val emailError = s.email.isNotEmpty() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(s.email)
        _state.update { it.copy(passengers = checked, phoneError = phoneError, emailError = emailError) }
        if (phoneError || emailError || checked.any { it.nameError || it.genderError || it.ageError || it.nidError }) return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                val booking = trains.hold(
                    TrainHoldRequest(
                        tripId = args.tripId,
                        trainClass = trainClass,
                        seats = seats,
                        passengers = checked.map { PassengerInput(it.name.trim(), it.gender!!, it.age.toInt(), it.seatNo, it.nid.ifBlank { null }) },
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
                    it.copy(submitting = false, error = e.error, mustReselect = code in setOf("seat_unavailable", "price_changed", "trip_not_found", "outside_sale_window"))
                }
                if (e.error is AppError.Api) savedStateHandle[KEY] = UUID.randomUUID().toString()
            }
        }
    }

    private companion object {
        const val KEY = "idempotency_key"
    }
}
