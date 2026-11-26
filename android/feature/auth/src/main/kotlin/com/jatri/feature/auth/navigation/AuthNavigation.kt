package com.jatri.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jatri.feature.auth.LoginRoute
import com.jatri.feature.auth.NameEntryRoute
import com.jatri.feature.auth.OnboardingRoute
import com.jatri.feature.auth.OtpRoute
import kotlinx.serialization.Serializable

@Serializable data object OnboardingDestination
@Serializable data object LoginDestination
@Serializable data class OtpDestination(val phone: String)
@Serializable data class NameEntryDestination(val phone: String)

fun NavController.navigateToLogin(navOptions: NavOptions? = null) = navigate(LoginDestination, navOptions)

fun NavGraphBuilder.onboardingScreen(onFinished: () -> Unit) {
    composable<OnboardingDestination> { OnboardingRoute(onFinished = onFinished) }
}

/**
 * Phone → OTP → (new users only) name. Talks to the backend OTP endpoints, or to the mock backend in builds
 * without a server.
 */
fun NavGraphBuilder.authFlow(
    navController: NavController,
    onAuthFinished: () -> Unit,
    onContinueAsGuest: () -> Unit,
) {
    composable<LoginDestination> {
        LoginRoute(
            onBack = { navController.popBackStack() },
            onCodeSent = { phone -> navController.navigate(OtpDestination(phone)) },
            onContinueAsGuest = onContinueAsGuest,
        )
    }
    composable<OtpDestination> { entry ->
        val route = entry.toRoute<OtpDestination>()
        OtpRoute(
            onBack = { navController.popBackStack() },
            onVerified = { isNew -> if (isNew) navController.navigate(NameEntryDestination(route.phone)) else onAuthFinished() },
        )
    }
    composable<NameEntryDestination> {
        // Back from the name step still leaves the user logged in; the name can be set later.
        NameEntryRoute(onBack = onAuthFinished, onDone = onAuthFinished)
    }
}
