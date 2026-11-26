package com.jatri.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.network.NetworkMonitor
import com.jatri.core.designsystem.component.OfflineBanner
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.TravelMode
import com.jatri.feature.account.navigation.accountScreens
import com.jatri.feature.auth.navigation.LoginDestination
import com.jatri.feature.auth.navigation.OnboardingDestination
import com.jatri.feature.auth.navigation.authFlow
import com.jatri.feature.auth.navigation.navigateToLogin
import com.jatri.feature.auth.navigation.onboardingScreen
import com.jatri.feature.booking.navigation.bookingScreens
import com.jatri.feature.booking.navigation.navigateToPassengerDetails
import com.jatri.feature.bus.navigation.busScreens
import com.jatri.feature.bus.navigation.navigateToBusResults
import com.jatri.feature.flight.navigation.flightScreens
import com.jatri.feature.flight.navigation.navigateToFlightResults
import com.jatri.feature.home.navigation.HomeDestination
import com.jatri.feature.home.navigation.homeScreen
import com.jatri.feature.notifications.navigation.navigateToNotifications
import com.jatri.feature.notifications.navigation.notificationsScreen
import com.jatri.feature.offers.navigation.offersScreen
import com.jatri.feature.payment.navigation.navigateToReviewPay
import com.jatri.feature.payment.navigation.paymentScreens
import com.jatri.feature.train.navigation.navigateToTrainResults
import com.jatri.feature.train.navigation.trainScreens
import com.jatri.feature.trips.navigation.tripsScreens
import kotlinx.coroutines.flow.map

@Composable
fun JatriApp(startDestination: Any, networkMonitor: NetworkMonitor) {
    val navController = rememberNavController()
    val offlineFlow = remember(networkMonitor) { networkMonitor.isOnline.map { !it } }
    val isOffline by offlineFlow.collectAsStateWithLifecycle(initialValue = false)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                JatriBottomBar(current = currentTab, onSelect = { navController.navigateToTab(it) })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OfflineBanner(visible = isOffline)
            JatriNavHost(navController, startDestination)
        }
    }
}

@Composable
private fun JatriNavHost(navController: NavHostController, startDestination: Any) {
    val toPassengers = { navController.navigateToPassengerDetails() }
    NavHost(navController = navController, startDestination = startDestination) {
        onboardingScreen(
            onFinished = {
                navController.navigate(HomeDestination) { popUpTo<OnboardingDestination> { inclusive = true } }
            },
        )
        authFlow(
            navController = navController,
            onAuthFinished = { navController.popBackStack<LoginDestination>(inclusive = true) },
            onContinueAsGuest = { navController.popBackStack<LoginDestination>(inclusive = true) },
        )
        homeScreen(
            onSearch = { navController.navigateToResults(it) },
            onNotificationsClick = { navController.navigateToNotifications() },
            onSeeAllOffers = { navController.navigateToTab(TopLevelDestination.OFFERS) },
        )
        flightScreens(navController, onContinueToPassengers = toPassengers)
        busScreens(navController, onContinueToPassengers = toPassengers)
        trainScreens(navController, onContinueToPassengers = toPassengers)
        bookingScreens(navController, onContinueToPayment = { navController.navigateToReviewPay() })
        paymentScreens(
            navController = navController,
            onViewTrips = { navController.navigateToTab(TopLevelDestination.TRIPS) },
            onGoHome = { navController.navigateToTab(TopLevelDestination.HOME) },
        )
        tripsScreens(navController, onSearchTickets = { navController.navigateToTab(TopLevelDestination.HOME) })
        offersScreen()
        accountScreens(
            navController = navController,
            onLoginClick = { navController.navigateToLogin() },
            onNotificationsClick = { navController.navigateToNotifications() },
        )
        notificationsScreen(onBack = { navController.popBackStack() })
    }
}

private fun NavHostController.navigateToResults(query: SearchQuery) = when (query.mode) {
    TravelMode.FLIGHT -> navigateToFlightResults(query)
    TravelMode.BUS -> navigateToBusResults(query)
    TravelMode.TRAIN -> navigateToTrainResults(query)
}

/** Standard bottom-nav behaviour: one copy of each tab, state saved and restored when switching. */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo<HomeDestination> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.isOn(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true

@Composable
private fun JatriBottomBar(current: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null) },
                label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = JatriTheme.colors.textSecondary,
                    unselectedTextColor = JatriTheme.colors.textSecondary,
                ),
            )
        }
    }
}

