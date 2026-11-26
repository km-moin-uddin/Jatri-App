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
data class BusSeatsDestination(val tripId: String, val fromId: String, val toId: String, val date: String)

fun NavController.navigateToBusResults(query: SearchQuery) = navigate(
    BusResultsDestination(
        fromId = requireNotNull(query.from).id,
        toId = requireNotNull(query.to).id,
        date = query.date.toString(),
        coach = query.coachType.name,
    ),
)

fun NavGraphBuilder.busScreens(navController: NavController, onContinueToPassengers: () -> Unit) {
    composable<BusResultsDestination> {
        BusResultsRoute(
            onBack = { navController.popBackStack() },
            onTripClick = { trip, args -> navController.navigate(BusSeatsDestination(trip.id, args.fromId, args.toId, args.date)) },
        )
    }
    composable<BusSeatsDestination> {
        BusSeatsRoute(onBack = { navController.popBackStack() }, onContinue = onContinueToPassengers)
    }
}
