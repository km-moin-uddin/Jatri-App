package com.jatri.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jatri.feature.auth.LoginScreen
import com.jatri.feature.auth.NameEntryScreen
import com.jatri.feature.auth.OnboardingRoute
import com.jatri.feature.auth.OtpScreen
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
 * Phone → OTP → name. Phase 1 is UI only: no SMS is sent and the OTP isn't checked
 * (wired to the backend in Phase 2).
 */
fun NavGraphBuilder.authFlow(
    navController: NavController,
    onAuthFinished: () -> Unit,
    onContinueAsGuest: () -> Unit,
) {
    composable<LoginDestination> {
        LoginScreen(
            onBack = { navController.popBackStack() },
            onCodeSent = { phone -> navController.navigate(OtpDestination(phone)) },
            onContinueAsGuest = onContinueAsGuest,
        )
    }
    composable<OtpDestination> { entry ->
        val route = entry.toRoute<OtpDestination>()
        OtpScreen(
            phone = route.phone,
            onBack = { navController.popBackStack() },
            onVerified = { navController.navigate(NameEntryDestination(route.phone)) },
        )
    }
    composable<NameEntryDestination> {
        NameEntryScreen(onBack = { navController.popBackStack() }, onDone = onAuthFinished)
    }
}
