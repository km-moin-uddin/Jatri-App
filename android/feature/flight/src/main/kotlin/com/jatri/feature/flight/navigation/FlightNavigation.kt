package com.jatri.feature.flight.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.core.data.repository.FlightSearchLeg
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.TravelMode
import com.jatri.core.model.TripType
import com.jatri.feature.flight.FlightDetailsRoute
import com.jatri.feature.flight.FlightResultsRoute
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class FlightResultsDestination(
    /** Journeys as "fromId,toId,2026-12-01" joined by ";" (1 = one way, 2 = round trip, up to 4). */
    val legs: String,
    val adults: Int,
    val children: Int,
    val infants: Int,
    val cabin: String,
    val tripType: String = TripType.ONE_WAY.name,
) {
    val searchLegs: List<FlightSearchLeg>
        get() = legs.split(';').filter(String::isNotBlank).map {
            val (from, to, date) = it.split(',')
            FlightSearchLeg(from, to, LocalDate.parse(date))
        }

    fun withLegs(legs: List<FlightSearchLeg>) = copy(legs = encode(legs))

    companion object {
        fun encode(legs: List<FlightSearchLeg>) = legs.joinToString(";") { "${it.fromId},${it.toId},${it.date}" }
    }
}

/** [shownPrice] (paisa) is what the results list showed, so a live re-price can be pointed out. */
@Serializable
data class FlightDetailsDestination(val offerId: String, val shownPrice: Long = 0)

/** The journeys a home search asks for: one way, there-and-back, or every multi-city flight. */
fun SearchQuery.flightLegs(): List<FlightSearchLeg> {
    val first = FlightSearchLeg(requireNotNull(from).id, requireNotNull(to).id, date)
    return when (tripType) {
        TripType.ONE_WAY -> listOf(first)
        TripType.ROUND_TRIP -> listOf(first, FlightSearchLeg(first.toId, first.fromId, returnDate ?: date.plusDays(3)))
        TripType.MULTI_CITY -> listOf(first) + extraLegs.map { FlightSearchLeg(requireNotNull(it.from).id, requireNotNull(it.to).id, it.date) }
    }
}

fun NavController.navigateToFlightResults(query: SearchQuery) {
    require(query.mode == TravelMode.FLIGHT)
    navigate(
        FlightResultsDestination(
            legs = FlightResultsDestination.encode(query.flightLegs()),
            adults = query.passengers.adults,
            children = query.passengers.children,
            infants = query.passengers.infants,
            cabin = query.cabinClass.name,
            tripType = query.tripType.name,
        ),
    )
}

fun NavGraphBuilder.flightScreens(navController: NavController, onContinueToPassengers: (offerId: String) -> Unit) {
    composable<FlightResultsDestination> {
        FlightResultsRoute(
            onBack = { navController.popBackStack() },
            onOfferClick = { offer -> navController.navigate(FlightDetailsDestination(offer.id, offer.price.paisa)) },
        )
    }
    composable<FlightDetailsDestination> {
        FlightDetailsRoute(onBack = { navController.popBackStack() }, onContinue = onContinueToPassengers)
    }
}
