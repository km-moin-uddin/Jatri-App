package com.jatri.feature.bus.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.core.model.SearchQuery
import com.jatri.feature.bus.BusResultsRoute
import com.jatri.feature.bus.BusSeatsRoute
import kotlinx.serialization.Serializable

@Serializable
data class BusResultsDestination(val fromId: String, val toId: String, val date: String, val coach: String)

@Serializable
data class BusSeatsDestination(val tripId: String)

/** What the seat screen hands to the passenger form. */
data class BusSelection(val tripId: String, val seats: List<String>, val boardingPointId: String, val droppingPointId: String)

fun NavController.navigateToBusResults(query: SearchQuery) = navigate(
    BusResultsDestination(
        fromId = requireNotNull(query.from).id,
        toId = requireNotNull(query.to).id,
        date = query.date.toString(),
        coach = query.coachType.name,
    ),
)

fun NavGraphBuilder.busScreens(navController: NavController, onContinueToPassengers: (BusSelection) -> Unit) {
    composable<BusResultsDestination> {
        BusResultsRoute(
            onBack = { navController.popBackStack() },
            onTripClick = { trip -> navController.navigate(BusSeatsDestination(trip.id)) },
        )
    }
    composable<BusSeatsDestination> {
        BusSeatsRoute(onBack = { navController.popBackStack() }, onContinue = onContinueToPassengers)
    }
}
