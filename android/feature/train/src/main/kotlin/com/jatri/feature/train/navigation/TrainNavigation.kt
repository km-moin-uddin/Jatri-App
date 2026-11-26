package com.jatri.feature.train.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.core.model.SearchQuery
import com.jatri.feature.train.TrainResultsRoute
import com.jatri.feature.train.TrainSeatRoute
import kotlinx.serialization.Serializable

@Serializable
data class TrainResultsDestination(
    val fromId: String,
    val toId: String,
    val date: String,
    val trainClass: String,
    val adults: Int,
    val children: Int,
)

@Serializable
data class TrainSeatDestination(val tripId: String, val trainClass: String)

fun NavController.navigateToTrainResults(query: SearchQuery) = navigate(
    TrainResultsDestination(
        fromId = requireNotNull(query.from).id,
        toId = requireNotNull(query.to).id,
        date = query.date.toString(),
        trainClass = query.trainClass.name,
        adults = query.passengers.adults,
        children = query.passengers.children,
    ),
)

fun NavGraphBuilder.trainScreens(navController: NavController, onContinueToPassengers: () -> Unit) {
    composable<TrainResultsDestination> {
        TrainResultsRoute(
            onBack = { navController.popBackStack() },
            onClassClick = { trip, fare -> navController.navigate(TrainSeatDestination(trip.id, fare.trainClass.name)) },
        )
    }
    composable<TrainSeatDestination> {
        TrainSeatRoute(onBack = { navController.popBackStack() }, onContinue = onContinueToPassengers)
    }
}
