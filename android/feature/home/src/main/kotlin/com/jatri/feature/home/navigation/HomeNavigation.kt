package com.jatri.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.core.model.SearchQuery
import com.jatri.feature.home.HomeRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

fun NavGraphBuilder.homeScreen(
    onSearch: (SearchQuery) -> Unit,
    onNotificationsClick: () -> Unit,
    onSeeAllOffers: () -> Unit,
) {
    composable<HomeDestination> {
        HomeRoute(onSearch = onSearch, onNotificationsClick = onNotificationsClick, onSeeAllOffers = onSeeAllOffers)
    }
}
