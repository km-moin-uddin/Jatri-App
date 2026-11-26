package com.jatri.core.model

import java.time.Duration
import java.time.LocalDateTime

/** One flight option in search results (normalised from any provider). */
data class FlightOffer(
    val id: String,
    val airlineName: String,
    val airlineLogoUrl: String?,
    val flightNumber: String,
    val from: Location,
    val to: Location,
    val departAt: LocalDateTime,
    val arriveAt: LocalDateTime,
    val stops: Int,
    val baggageKg: Int,
    val refundable: Boolean,
    val cabinClass: CabinClass,
    val price: Money,
) {
    val duration: Duration get() = Duration.between(departAt, arriveAt)
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

enum class SeatState { AVAILABLE, SELECTED, BOOKED, LADIES }

data class Seat(val number: String, val state: SeatState)

/**
 * A bus seat layout. Each row is a list of seats where `null` marks the aisle gap.
 * A 2+2 coach row looks like `[A1, A2, null, A3, A4]`.
 */
data class SeatLayout(val rows: List<List<Seat?>>) {
    companion object {
        const val MAX_SEATS_PER_BOOKING = 4
    }
}
