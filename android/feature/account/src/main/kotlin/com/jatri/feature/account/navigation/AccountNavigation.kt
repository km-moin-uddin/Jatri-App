package com.jatri.feature.account.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jatri.feature.account.AccountRoute
import com.jatri.feature.account.AccountSubPage
import com.jatri.feature.account.AccountSubPageScreen
import com.jatri.feature.account.HelpScreen
import com.jatri.feature.account.ProfileRoute
import kotlinx.serialization.Serializable

@Serializable data object AccountDestination
@Serializable data class AccountSubPageDestination(val page: AccountSubPage)
@Serializable data object HelpDestination

fun NavGraphBuilder.accountScreens(
    navController: NavController,
    onLoginClick: () -> Unit,
    onNotificationsClick: () -> Unit,
) {
    composable<AccountDestination> {
        AccountRoute(
            onLoginClick = onLoginClick,
            onNotificationsClick = onNotificationsClick,
            onOpenPage = { navController.navigate(AccountSubPageDestination(it)) },
            onHelpClick = { navController.navigate(HelpDestination) },
        )
    }
    composable<AccountSubPageDestination> { entry ->
        val page = entry.toRoute<AccountSubPageDestination>().page
        if (page == AccountSubPage.PROFILE) {
            ProfileRoute(onBack = { navController.popBackStack() })
        } else {
            AccountSubPageScreen(page = page, onBack = { navController.popBackStack() })
        }
    }
    composable<HelpDestination> { HelpScreen(onBack = { navController.popBackStack() }) }
}
