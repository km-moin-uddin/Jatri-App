package com.jatri.core.model

import java.time.Duration
import java.time.LocalDateTime

/** One flight (take-off to landing). Times are local to each airport, as on a boarding pass. */
data class FlightLeg(
    val carrierCode: String,
    val carrierName: String,
    val flightNumber: String,
    val aircraft: String,
    val from: Location,
    val to: Location,
    val departAt: LocalDateTime,
    val arriveAt: LocalDateTime,
    val duration: Duration,
)

/** One journey of a trip: the outbound, the return, or one city pair of a multi-city trip. */
data class FlightJourney(
    val from: Location,
    val to: Location,
    val departAt: LocalDateTime,
    val arriveAt: LocalDateTime,
    /** Real elapsed time (local clocks can differ across time zones). */
    val duration: Duration,
    val legs: List<FlightLeg>,
) {
    val stops: Int get() = legs.size - 1

    /** Calendar days between take-off and landing in local time (shown as "+1"). */
    val dayShift: Int get() = java.time.temporal.ChronoUnit.DAYS.between(departAt.toLocalDate(), arriveAt.toLocalDate()).toInt()
}

enum class FareBrand { SAVER, FLEX, BUSINESS }

/** What a fare allows. Fees are per passenger; null = not allowed. */
data class FareRules(
    val refundable: Boolean,
    val refundFee: Money?,
    val changeFee: Money?,
    /** Refunds/changes close this many hours before departure. */
    val deadlineHours: Int,
    val checkedBaggageKg: Int,
    val cabinBaggageKg: Int,
)

/** Fare for one passenger type, per passenger. */
data class PaxFare(val type: PassengerType, val count: Int, val base: Money, val tax: Money)

/** One flight offer in search results (normalised from any provider). */
data class FlightOffer(
    val id: String,
    val journeys: List<FlightJourney>,
    val cabinClass: CabinClass,
    val fareBrand: FareBrand,
    val rules: FareRules,
    val fares: List<PaxFare>,
    val baseTotal: Money,
    val taxTotal: Money,
    val serviceCharge: Money,
    /** Everything the customer pays. */
    val price: Money,
    val seatsLeft: Int,
    /** Passport number + expiry needed for every passenger. */
    val international: Boolean,
) {
    val first: FlightJourney get() = journeys.first()
    val airlineName: String get() = first.legs.first().carrierName
    val airlines: Set<String> get() = journeys.flatMap { j -> j.legs.map { it.carrierName } }.toSet()
    val departAt: LocalDateTime get() = first.departAt
    val maxStops: Int get() = journeys.maxOf { it.stops }

    /** Total time in the air and at connections, all journeys together. */
    val duration: Duration get() = journeys.fold(Duration.ZERO) { acc, j -> acc + j.duration }

    fun count(type: PassengerType): Int = fares.firstOrNull { it.type == type }?.count ?: 0
}

/** A boarding or dropping counter, with the coach's time there. */
data class StopPoint(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val time: LocalDateTime,
) {
    fun name(language: AppLanguage) = if (language == AppLanguage.BANGLA) nameBn else nameEn
}

/** One bus trip in search results. */
data class BusTrip(
    val id: String,
    val operatorName: String,
    val operatorLogoUrl: String?,
    val coachNumber: String,
    val coachType: CoachType,
    val from: Location,
    val to: Location,
    val departAt: LocalDateTime,
    val arriveAt: LocalDateTime,
    val boardingPoint: String,
    val seatsAvailable: Int,
    val price: Money,
    val boardingPoints: List<StopPoint> = emptyList(),
    val droppingPoints: List<StopPoint> = emptyList(),
)

/** Seat availability and fare for one class of a train. */
data class TrainClassFare(
    val trainClass: TrainClass,
    val seatsAvailable: Int,
    val price: Money,
)

/** One train in search results. */
data class TrainTrip(
    val id: String,
    val trainName: String,
    val trainNumber: String,
    val from: Location,
    val to: Location,
    val departAt: LocalDateTime,
    val arriveAt: LocalDateTime,
    val offDay: java.time.DayOfWeek?,
    val classes: List<TrainClassFare>,
)

/** HELD = someone else is paying for it right now (shown like booked). */
enum class SeatState { AVAILABLE, SELECTED, BOOKED, LADIES, HELD }

data class Seat(val number: String, val state: SeatState)

/**
 * A bus seat layout. Each row is a list of seats where `null` marks the aisle gap.
 * A 2+2 coach row looks like `[A1, A2, null, A3, A4]`.
 */
data class SeatLayout(val rows: List<List<Seat?>>) {
    fun find(number: String): Seat? = rows.asSequence().flatten().filterNotNull().firstOrNull { it.number == number }

    companion object {
        const val MAX_SEATS_PER_BOOKING = 4
    }
}

/** Seat map for one trip, plus the numbers needed to price a booking. */
data class BusSeatMap(
    val trip: BusTrip,
    val layout: SeatLayout,
    val maxSelectable: Int,
    val serviceChargePerSeat: Money,
)
