package com.jatri.core.data.repository

import com.jatri.core.model.Booking
import com.jatri.core.model.CabinClass
import com.jatri.core.model.FareBrand
import com.jatri.core.model.FareRules
import com.jatri.core.model.FlightHoldRequest
import com.jatri.core.model.FlightJourney
import com.jatri.core.model.FlightLeg
import com.jatri.core.model.FlightOffer
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.Money
import com.jatri.core.model.PassengerType
import com.jatri.core.model.PaxFare
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.apiCall
import com.jatri.core.network.model.FareRulesDto
import com.jatri.core.network.model.FlightBookingRequest
import com.jatri.core.network.model.FlightOfferDto
import com.jatri.core.network.model.FlightPassengerInDto
import com.jatri.core.network.model.FlightSearchRequest
import com.jatri.core.network.model.SliceDto
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One journey of a flight search: from, to and the day. */
data class FlightSearchLeg(val fromId: String, val toId: String, val date: LocalDate)

interface FlightRepository {
    suspend fun search(legs: List<FlightSearchLeg>, adults: Int, children: Int, infants: Int, cabin: CabinClass): List<FlightOffer>

    /** Live price and fare rules for one offer (`offer_expired` if it's gone). */
    suspend fun offer(id: String): FlightOffer

    /** Holds the fare; the airline creates a PNR. Status `held` with a countdown, like bus holds. */
    suspend fun hold(request: FlightHoldRequest): Booking
}

@Singleton
internal class NetworkFlightRepository @Inject constructor(
    private val api: JatriApi,
    private val locations: LocationRepository,
) : FlightRepository {
    override suspend fun search(legs: List<FlightSearchLeg>, adults: Int, children: Int, infants: Int, cabin: CabinClass): List<FlightOffer> {
        val body = FlightSearchRequest(
            slices = legs.map { SliceDto(it.fromId, it.toId, it.date.toString()) },
            adults = adults,
            children = children,
            infants = infants,
            cabin = cabin.name.lowercase(),
        )
        return apiCall { api.searchFlights(body) }.map { it.toModel(locations) }
    }

    override suspend fun offer(id: String): FlightOffer = apiCall { api.flightOffer(id) }.toModel(locations)

    override suspend fun hold(request: FlightHoldRequest): Booking {
        val body = FlightBookingRequest(
            mode = "flight",
            offerId = request.offerId,
            passengers = request.passengers.map {
                FlightPassengerInDto(
                    type = it.type.name.lowercase(),
                    givenName = it.givenName.trim(),
                    surname = it.surname.trim(),
                    gender = it.gender.name.lowercase(),
                    dateOfBirth = it.dateOfBirth.toString(),
                    nationality = it.nationality,
                    documentNo = it.documentNo?.trim()?.uppercase()?.takeIf(String::isNotBlank),
                    documentExpiry = it.documentExpiry?.toString(),
                )
            },
            contactPhone = request.contactPhone,
            contactEmail = request.contactEmail?.takeIf(String::isNotBlank),
            expectedTotal = request.expectedTotal.paisa,
        )
        return apiCall { api.createFlightBooking(body, request.idempotencyKey) }.toModel()
    }
}

// ---- mapping -----------------------------------------------------------------------------

internal suspend fun FlightOfferDto.toModel(locations: LocationRepository): FlightOffer {
    suspend fun place(id: String, code: String) = locations.getById(id) ?: Location(id, LocationType.AIRPORT, code, code, code, code, code)
    return FlightOffer(
        id = id,
        journeys = slices.map { s ->
            FlightJourney(
                from = place(s.fromId, s.fromId.removePrefix("air_")),
                to = place(s.toId, s.toId.removePrefix("air_")),
                departAt = parseWallTime(s.departAt),
                arriveAt = parseWallTime(s.arriveAt),
                duration = Duration.ofMinutes(s.durationMinutes.toLong()),
                legs = s.legs.map { l ->
                    FlightLeg(
                        carrierCode = l.carrierCode,
                        carrierName = l.carrierName,
                        flightNumber = l.flightNumber,
                        aircraft = l.aircraft,
                        from = place(l.fromId, l.fromCode),
                        to = place(l.toId, l.toCode),
                        departAt = parseWallTime(l.departAt),
                        arriveAt = parseWallTime(l.arriveAt),
                        duration = Duration.ofMinutes(l.durationMinutes.toLong()),
                    )
                },
            )
        },
        cabinClass = cabinClass(cabin),
        fareBrand = fareBrand(fareBrand),
        rules = rules.toModel(),
        fares = fares.map { PaxFare(passengerType(it.type), it.count, Money(it.base), Money(it.tax)) },
        baseTotal = Money(baseTotal),
        taxTotal = Money(taxTotal),
        serviceCharge = Money(serviceCharge),
        price = Money(total),
        seatsLeft = seatsLeft,
        international = international,
    )
}

internal fun FareRulesDto.toModel() = FareRules(
    refundable = refundable,
    refundFee = refundFee?.let(::Money),
    changeFee = changeFee?.let(::Money),
    deadlineHours = deadlineHours,
    checkedBaggageKg = checkedBaggageKg,
    cabinBaggageKg = cabinBaggageKg,
)

internal fun cabinClass(value: String) = when (value) {
    "business" -> CabinClass.BUSINESS
    "premium_economy" -> CabinClass.PREMIUM_ECONOMY
    else -> CabinClass.ECONOMY
}

internal fun fareBrand(value: String) = when (value) {
    "flex" -> FareBrand.FLEX
    "business" -> FareBrand.BUSINESS
    else -> FareBrand.SAVER
}

internal fun passengerType(value: String) = when (value) {
    "child" -> PassengerType.CHILD
    "infant" -> PassengerType.INFANT
    else -> PassengerType.ADULT
}
