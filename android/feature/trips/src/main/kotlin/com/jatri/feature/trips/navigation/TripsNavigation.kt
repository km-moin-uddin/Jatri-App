package com.jatri.feature.trips.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.jatri.feature.trips.TicketDetailScreen
import com.jatri.feature.trips.TripsScreen
import kotlinx.serialization.Serializable

@Serializable data object TripsDestination
@Serializable data class TicketDetailDestination(val bookingId: String)

fun NavController.navigateToTrips(navOptions: NavOptions? = null) = navigate(TripsDestination, navOptions)

fun NavGraphBuilder.tripsScreens(navController: NavController, onSearchTickets: () -> Unit) {
    composable<TripsDestination> {
        TripsScreen(
            onSearchTickets = onSearchTickets,
            onTicketClick = { navController.navigate(TicketDetailDestination(it)) },
        )
    }
    composable<TicketDetailDestination> {
        TicketDetailScreen(onBack = { navController.popBackStack() })
    }
}
