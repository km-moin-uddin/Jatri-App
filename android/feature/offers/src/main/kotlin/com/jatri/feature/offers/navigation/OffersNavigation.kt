package com.jatri.feature.offers.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.feature.offers.OffersRoute
import kotlinx.serialization.Serializable

@Serializable data object OffersDestination

fun NavGraphBuilder.offersScreen() {
    composable<OffersDestination> { OffersRoute() }
}
