package com.jatri.app.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.jatri.app.R
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.feature.account.navigation.AccountDestination
import com.jatri.feature.home.navigation.HomeDestination
import com.jatri.feature.offers.navigation.OffersDestination
import com.jatri.feature.trips.navigation.TripsDestination
import kotlin.reflect.KClass

/** The four bottom-navigation tabs: Home · My Trips · Offers · Account. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val label: Int,
) {
    HOME(HomeDestination, HomeDestination::class, JatriIcons.Home, JatriIcons.HomeOutlined, R.string.nav_home),
    TRIPS(TripsDestination, TripsDestination::class, JatriIcons.Ticket, JatriIcons.TicketOutlined, R.string.nav_trips),
    OFFERS(OffersDestination, OffersDestination::class, JatriIcons.Offer, JatriIcons.OfferOutlined, R.string.nav_offers),
    ACCOUNT(AccountDestination, AccountDestination::class, JatriIcons.Account, JatriIcons.AccountOutlined, R.string.nav_account),
}
