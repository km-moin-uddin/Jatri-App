package com.jatri.core.model

import java.time.LocalDate

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
) {
    val isComplete: Boolean get() = from != null && to != null && from.id != to.id
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
