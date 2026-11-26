package com.jatri.feature.booking.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jatri.feature.booking.BusPassengerRoute
import com.jatri.feature.booking.FlightPassengerRoute
import com.jatri.feature.booking.PassengerPreviewScreen
import kotlinx.serialization.Serializable

/**
 * Passenger details. Buses carry the chosen trip/seats/points (comma-separated seats); flights
 * carry the offer id in [tripId]; trains still show the layout preview until Phase 6.
 */
@Serializable
data class PassengerDetailsDestination(
    val mode: String,
    val tripId: String = "",
    val seats: String = "",
    val boardingPointId: String = "",
    val droppingPointId: String = "",
)

fun NavController.navigateToPassengerDetails(destination: PassengerDetailsDestination) = navigate(destination)

fun NavGraphBuilder.bookingScreens(
    navController: NavController,
    onHeld: (bookingId: String) -> Unit,
    onPreviewContinue: () -> Unit,
) {
    composable<PassengerDetailsDestination> { entry ->
        val args = entry.toRoute<PassengerDetailsDestination>()
        if (args.mode == "bus") {
            BusPassengerRoute(onBack = { navController.popBackStack() }, onHeld = onHeld)
        } else if (args.mode == "flight" && args.tripId.isNotEmpty()) {
            FlightPassengerRoute(onBack = { navController.popBackStack() }, onHeld = onHeld)
        } else {
            PassengerPreviewScreen(onBack = { navController.popBackStack() }, onContinue = onPreviewContinue)
        }
    }
}
