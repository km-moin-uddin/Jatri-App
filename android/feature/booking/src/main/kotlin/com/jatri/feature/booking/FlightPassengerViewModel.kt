package com.jatri.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.FlightRepository
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.FlightHoldRequest
import com.jatri.core.model.FlightOffer
import com.jatri.core.model.FlightPassengerInput
import com.jatri.core.model.Gender
import com.jatri.core.model.PassengerType
import com.jatri.core.model.SessionState
import com.jatri.feature.booking.navigation.PassengerDetailsDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import javax.inject.Inject

/** Dates are typed as 8 digits (DDMMYYYY) and shown as DD/MM/YYYY. */
internal fun parseDigitsDate(digits: String): LocalDate? =
    if (digits.length != 8) null else runCatching {
        LocalDate.of(digits.substring(4).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
    }.getOrNull()

private val LATIN_NAME = Regex("^[A-Za-z][A-Za-z .'-]*$")
private val PASSPORT = Regex("^[A-Z0-9]{6,9}$")
private val NID = Regex("^\\d{10}$|^\\d{13}$|^\\d{17}$")

enum class FlightFieldError { REQUIRED, LATIN_ONLY, DATE_INVALID, AGE_MISMATCH, PASSPORT_INVALID, PASSPORT_EXPIRY, DOCUMENT_INVALID }

data class FlightPassengerForm(
    val type: PassengerType,
    val givenName: String = "",
    val surname: String = "",
    val gender: Gender? = null,
    /** Raw digits, DDMMYYYY. */
    val dob: String = "",
    val documentNo: String = "",
    val expiry: String = "",
    val nationality: String = "BD",
    val givenNameError: FlightFieldError? = null,
    val surnameError: FlightFieldError? = null,
    val genderError: Boolean = false,
    val dobError: FlightFieldError? = null,
    val documentError: FlightFieldError? = null,
    val expiryError: FlightFieldError? = null,
    val nationalityError: Boolean = false,
) {
    val hasError: Boolean
        get() = listOfNotNull(givenNameError, surnameError, dobError, documentError, expiryError).isNotEmpty() || genderError || nationalityError
}

data class FlightPassengerUiState(
    val loading: Boolean = true,
    val offer: FlightOffer? = null,
    val passengers: List<FlightPassengerForm> = emptyList(),
    val phone: String = "",
    val email: String = "",
    val phoneError: Boolean = false,
    val emailError: Boolean = false,
    val submitting: Boolean = false,
    val error: AppError? = null,
    /** The fare changed or is gone: the user must pick a flight again. */
    val mustReselect: Boolean = false,
)

@HiltViewModel
class FlightPassengerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val flights: FlightRepository,
    auth: AuthRepository,
    private val clock: Clock,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<PassengerDetailsDestination>()
    private val user = (auth.session.value as? SessionState.LoggedIn)?.user

    private val idempotencyKey: String
        get() = savedStateHandle.get<String>(KEY) ?: UUID.randomUUID().toString().also { savedStateHandle[KEY] = it }

    private val _state = MutableStateFlow(FlightPassengerUiState(phone = user?.phone.orEmpty(), email = user?.email.orEmpty()))
    val state: StateFlow<FlightPassengerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val offer = flights.offer(args.tripId)
                val forms = PassengerType.entries.flatMap { t -> List(offer.count(t)) { FlightPassengerForm(t) } }
                // The account holder is usually the first adult: pre-fill the name, split on the last space.
                val name = user?.name.orEmpty().trim()
                val prefilled = forms.mapIndexed { i, f ->
                    if (i == 0 && name.isNotEmpty() && LATIN_NAME.matches(name)) {
                        f.copy(givenName = name.substringBeforeLast(' ', name), surname = name.substringAfterLast(' ', ""))
                    } else {
                        f
                    }
                }
                _state.update { it.copy(loading = false, offer = offer, passengers = it.passengers.ifEmpty { prefilled }) }
            } catch (e: AppException) {
                _state.update { it.copy(loading = false, error = e.error) }
            }
        }
    }

    fun update(index: Int, transform: (FlightPassengerForm) -> FlightPassengerForm) = _state.update { s ->
        s.copy(passengers = s.passengers.mapIndexed { i, p -> if (i == index) transform(p) else p }, error = null)
    }

    fun setPhone(value: String) = _state.update { it.copy(phone = value.filter(Char::isDigit).take(11), phoneError = false) }
    fun setEmail(value: String) = _state.update { it.copy(email = value.trim(), emailError = false) }
    fun dismissError() = _state.update { it.copy(error = null) }

    private fun check(p: FlightPassengerForm, offer: FlightOffer): FlightPassengerForm {
        val first = offer.journeys.first().departAt.toLocalDate()
        val last = offer.journeys.last().departAt.toLocalDate()
        fun nameError(v: String) = when {
            v.isBlank() -> FlightFieldError.REQUIRED
            !LATIN_NAME.matches(v.trim()) -> FlightFieldError.LATIN_ONLY
            else -> null
        }
        val dob = parseDigitsDate(p.dob)
        val dobError = when {
            p.dob.isEmpty() -> FlightFieldError.REQUIRED
            dob == null || dob.isAfter(LocalDate.now(clock)) -> FlightFieldError.DATE_INVALID
            !ageFits(p.type, dob, first, last) -> FlightFieldError.AGE_MISMATCH
            else -> null
        }
        val doc = p.documentNo.trim().uppercase()
        val expiry = parseDigitsDate(p.expiry)
        val documentError = when {
            offer.international && doc.isEmpty() -> FlightFieldError.REQUIRED
            offer.international && !PASSPORT.matches(doc) -> FlightFieldError.PASSPORT_INVALID
            !offer.international && doc.isNotEmpty() && !PASSPORT.matches(doc) && !NID.matches(doc) -> FlightFieldError.DOCUMENT_INVALID
            else -> null
        }
        val expiryError = when {
            !offer.international -> null
            p.expiry.isEmpty() -> FlightFieldError.REQUIRED
            expiry == null -> FlightFieldError.DATE_INVALID
            expiry.isBefore(last.plusMonths(6)) -> FlightFieldError.PASSPORT_EXPIRY
            else -> null
        }
        return p.copy(
            givenNameError = nameError(p.givenName),
            surnameError = nameError(p.surname),
            genderError = p.gender == null,
            dobError = dobError,
            documentError = documentError,
            expiryError = expiryError,
            nationalityError = offer.international && !Regex("^[A-Z]{2}$").matches(p.nationality),
        )
    }

    fun submit(onHeld: (String) -> Unit) {
        val s = _state.value
        val offer = s.offer ?: return
        if (s.submitting) return
        val checked = s.passengers.map { check(it, offer) }
        val phoneError = !Regex("^01[3-9]\\d{8}$").matches(s.phone)
        val emailError = s.email.isNotEmpty() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(s.email)
        _state.update { it.copy(passengers = checked, phoneError = phoneError, emailError = emailError) }
        if (phoneError || emailError || checked.any { it.hasError }) return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                val booking = flights.hold(
                    FlightHoldRequest(
                        offerId = offer.id,
                        passengers = checked.map {
                            FlightPassengerInput(
                                type = it.type,
                                givenName = it.givenName.trim(),
                                surname = it.surname.trim(),
                                gender = it.gender!!,
                                dateOfBirth = parseDigitsDate(it.dob)!!,
                                nationality = it.nationality,
                                documentNo = it.documentNo.trim().uppercase().ifBlank { null },
                                documentExpiry = parseDigitsDate(it.expiry),
                            )
                        },
                        contactPhone = s.phone,
                        contactEmail = s.email.ifBlank { null },
                        expectedTotal = offer.price,
                        idempotencyKey = idempotencyKey,
                    ),
                )
                _state.update { it.copy(submitting = false) }
                onHeld(booking.id)
            } catch (e: AppException) {
                val code = (e.error as? AppError.Api)?.code
                _state.update { it.copy(submitting = false, error = e.error, mustReselect = code in setOf("price_changed", "offer_expired")) }
                if (e.error is AppError.Api) savedStateHandle[KEY] = UUID.randomUUID().toString()
            }
        }
    }

    private companion object {
        const val KEY = "idempotency_key"
    }
}

/** Airline age bands: adult 12+, child 2-11 for the whole trip, infant under 2 for the whole trip. */
internal fun ageFits(type: PassengerType, dob: LocalDate, firstFlight: LocalDate, lastFlight: LocalDate): Boolean {
    val atFirst = Period.between(dob, firstFlight).years
    val atLast = Period.between(dob, lastFlight).years
    return when (type) {
        PassengerType.ADULT -> atFirst >= 12
        PassengerType.CHILD -> atFirst >= 2 && atLast < 12
        PassengerType.INFANT -> atLast < 2
    }
}
