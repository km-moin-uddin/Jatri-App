package com.jatri.core.model

import java.time.LocalDate

enum class TripType { ONE_WAY, ROUND_TRIP, MULTI_CITY }

/** One more flight of a multi-city trip (the first one is the query's own from/to/date). */
data class LegQuery(val from: Location?, val to: Location?, val date: LocalDate) {
    val isComplete: Boolean get() = from != null && to != null && from.id != to.id
}

/** Everything the user typed into the home search card for one mode. */
data class SearchQuery(
    val mode: TravelMode,
    val from: Location?,
    val to: Location?,
    val date: LocalDate,
    val returnDate: LocalDate? = null,
    val passengers: PassengerCount = PassengerCount(),
    val cabinClass: CabinClass = CabinClass.ECONOMY,
    val coachType: CoachType = CoachType.ANY,
    val trainClass: TrainClass = TrainClass.SNIGDHA,
    /** Flights only. */
    val tripType: TripType = TripType.ONE_WAY,
    /** Multi-city flights 2..4. */
    val extraLegs: List<LegQuery> = emptyList(),
) {
    val isComplete: Boolean get() = from != null && to != null && from.id != to.id

    companion object {
        const val MAX_FLIGHT_LEGS = 4
    }
}

/** A search the user ran before, shown under "Recent searches". */
data class RecentSearch(
    val id: Long,
    val mode: TravelMode,
    val from: Location,
    val to: Location,
    val date: LocalDate,
    val passengers: PassengerCount,
)

/** A route we promote on the home screen, e.g. Dhaka → Cox's Bazar. */
data class PopularRoute(
    val mode: TravelMode,
    val from: Location,
    val to: Location,
    val startingFrom: Money,
)
