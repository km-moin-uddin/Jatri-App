package com.jatri.feature.booking.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.feature.booking.PassengerDetailsScreen
import kotlinx.serialization.Serializable

@Serializable
data object PassengerDetailsDestination

fun NavController.navigateToPassengerDetails() = navigate(PassengerDetailsDestination)

fun NavGraphBuilder.bookingScreens(navController: NavController, onContinueToPayment: () -> Unit) {
    composable<PassengerDetailsDestination> {
        PassengerDetailsScreen(onBack = { navController.popBackStack() }, onContinue = onContinueToPayment)
    }
}
