package com.jatri.feature.flight.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.core.model.SearchQuery
import com.jatri.feature.flight.FlightDetailsRoute
import com.jatri.feature.flight.FlightResultsRoute
import kotlinx.serialization.Serializable

@Serializable
data class FlightResultsDestination(
    val fromId: String,
    val toId: String,
    /** ISO date, e.g. 2026-12-01 */
    val date: String,
    val adults: Int,
    val children: Int,
    val infants: Int,
    val cabin: String,
)

@Serializable
data class FlightDetailsDestination(val offerId: String, val fromId: String, val toId: String, val date: String)

fun NavController.navigateToFlightResults(query: SearchQuery) = navigate(
    FlightResultsDestination(
        fromId = requireNotNull(query.from).id,
        toId = requireNotNull(query.to).id,
        date = query.date.toString(),
        adults = query.passengers.adults,
        children = query.passengers.children,
        infants = query.passengers.infants,
        cabin = query.cabinClass.name,
    ),
)

fun NavGraphBuilder.flightScreens(navController: NavController, onContinueToPassengers: () -> Unit) {
    composable<FlightResultsDestination> {
        FlightResultsRoute(
            onBack = { navController.popBackStack() },
            onOfferClick = { offer, args ->
                navController.navigate(FlightDetailsDestination(offer.id, args.fromId, args.toId, args.date))
            },
        )
    }
    composable<FlightDetailsDestination> {
        FlightDetailsRoute(onBack = { navController.popBackStack() }, onContinue = onContinueToPassengers)
    }
}
