package com.jatri.feature.trips.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.jatri.feature.trips.TicketDetailRoute
import com.jatri.feature.trips.TripsRoute
import kotlinx.serialization.Serializable

@Serializable data object TripsDestination
@Serializable data class TicketDetailDestination(val bookingId: String)

fun NavController.navigateToTrips(navOptions: NavOptions? = null) = navigate(TripsDestination, navOptions)

fun NavController.navigateToTicket(bookingId: String) = navigate(TicketDetailDestination(bookingId))

fun NavGraphBuilder.tripsScreens(navController: NavController, onSearchTickets: () -> Unit, onLogin: () -> Unit) {
    composable<TripsDestination> {
        TripsRoute(
            onSearchTickets = onSearchTickets,
            onTicketClick = { navController.navigateToTicket(it) },
            onLogin = onLogin,
        )
    }
    composable<TicketDetailDestination> {
        TicketDetailRoute(onBack = { navController.popBackStack() })
    }
}
