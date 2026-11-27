package com.jatri.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.Spacing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jatri.feature.booking.navigation.PassengerDetailsDestination
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
import com.jatri.feature.trips.navigation.navigateToTicket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.LaunchedEffect
import com.jatri.feature.trips.navigation.tripsScreens
import kotlinx.coroutines.flow.map

@Composable
fun JatriApp(
    startDestination: Any,
    networkMonitor: NetworkMonitor,
    isLoggedIn: () -> Boolean,
    openBooking: StateFlow<String?> = MutableStateFlow(null),
    onBookingOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    // A tapped reminder opens its ticket (offline copy if needed).
    val bookingToOpen by openBooking.collectAsStateWithLifecycle()
    LaunchedEffect(bookingToOpen) {
        bookingToOpen?.let {
            navController.navigateToTicket(it)
            onBookingOpened()
        }
    }
    val offlineFlow = remember(networkMonitor) { networkMonitor.isOnline.map { !it } }
    val isOffline by offlineFlow.collectAsStateWithLifecycle(initialValue = false)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                JatriBottomBar(current = currentTab, onSelect = { navController.navigateToTab(it) })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OfflineBanner(visible = isOffline)
            JatriNavHost(navController, startDestination, isLoggedIn)
        }
    }
}

@Composable
private fun JatriNavHost(navController: NavHostController, startDestination: Any, isLoggedIn: () -> Boolean) {
    // Searching works as a guest; booking needs an account. After logging in, carry on to the form.
    var pendingBooking by remember { mutableStateOf<PassengerDetailsDestination?>(null) }
    val toPassengers: (PassengerDetailsDestination) -> Unit = { destination ->
        if (isLoggedIn()) {
            navController.navigateToPassengerDetails(destination)
        } else {
            pendingBooking = destination
            navController.navigateToLogin()
        }
    }
    val leaveAuth = {
        navController.popBackStack<LoginDestination>(inclusive = true)
        pendingBooking?.takeIf { isLoggedIn() }?.let(navController::navigateToPassengerDetails)
        pendingBooking = null
    }
    NavHost(navController = navController, startDestination = startDestination) {
        onboardingScreen(
            onFinished = {
                navController.navigate(HomeDestination) { popUpTo<OnboardingDestination> { inclusive = true } }
            },
        )
        authFlow(
            navController = navController,
            onAuthFinished = { leaveAuth() },
            onContinueAsGuest = { leaveAuth() },
        )
        homeScreen(
            onSearch = { navController.navigateToResults(it) },
            onNotificationsClick = { navController.navigateToNotifications() },
            onSeeAllOffers = { navController.navigateToTab(TopLevelDestination.OFFERS) },
            onAccountClick = { navController.navigateToTab(TopLevelDestination.ACCOUNT) },
        )
        flightScreens(navController, onContinueToPassengers = { offerId -> toPassengers(PassengerDetailsDestination(mode = "flight", tripId = offerId)) })
        busScreens(
            navController,
            onContinueToPassengers = { sel ->
                toPassengers(
                    PassengerDetailsDestination(
                        mode = "bus",
                        tripId = sel.tripId,
                        seats = sel.seats.joinToString(","),
                        boardingPointId = sel.boardingPointId,
                        droppingPointId = sel.droppingPointId,
                    ),
                )
            },
        )
        trainScreens(
            navController,
            onContinueToPassengers = { tripId, trainClass, seats ->
                toPassengers(PassengerDetailsDestination(mode = "train", tripId = tripId, seats = seats.joinToString(","), travelClass = trainClass.name))
            },
        )
        bookingScreens(
            navController,
            onHeld = { bookingId -> navController.navigateToReviewPay(bookingId) },
            onPreviewContinue = { navController.navigateToReviewPay() },
        )
        paymentScreens(
            navController = navController,
            onViewTrips = { navController.navigateToTab(TopLevelDestination.TRIPS) },
            onGoHome = { navController.navigateToTab(TopLevelDestination.HOME) },
        )
        tripsScreens(
            navController,
            onSearchTickets = { navController.navigateToTab(TopLevelDestination.HOME) },
            onLogin = { navController.navigateToLogin() },
        )
        offersScreen()
        accountScreens(
            navController = navController,
            onLoginClick = { navController.navigateToLogin() },
            onNotificationsClick = { navController.navigateToNotifications() },
        )
        notificationsScreen(onBack = { navController.popBackStack() }, onOpenBooking = { navController.navigateToTicket(it) })
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

/**
 * Floating pill navigation. The active tab is a black pill with icon + label; the others are round
 * icon buttons whose label is still read out by screen readers.
 */
@Composable
internal fun JatriBottomBar(current: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = JatriShapes.pill,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, JatriTheme.colors.cardBorder),
            shadowElevation = 10.dp,
        ) {
            Row(
                Modifier.padding(6.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { tab -> NavPill(tab, tab == current) { onSelect(tab) } }
            }
        }
    }
}

@Composable
private fun NavPill(tab: TopLevelDestination, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(tab.label)
    Surface(
        shape = JatriShapes.pill,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else JatriTheme.colors.textSecondary,
        modifier = Modifier
            .height(52.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label }
            .animateContentSize(tween(250)),
    ) {
        Row(
            Modifier.padding(horizontal = if (selected) 18.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null, modifier = Modifier.size(24.dp))
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
