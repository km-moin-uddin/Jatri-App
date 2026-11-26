package com.jatri.feature.payment.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jatri.feature.payment.PaymentResultScreen
import com.jatri.feature.payment.ReviewPayScreen
import kotlinx.serialization.Serializable

@Serializable data object ReviewPayDestination
@Serializable data class PaymentResultDestination(val success: Boolean)

fun NavController.navigateToReviewPay() = navigate(ReviewPayDestination)

fun NavGraphBuilder.paymentScreens(navController: NavController, onViewTrips: () -> Unit, onGoHome: () -> Unit) {
    composable<ReviewPayDestination> {
        ReviewPayScreen(
            onBack = { navController.popBackStack() },
            onPaid = { success ->
                navController.navigate(PaymentResultDestination(success)) {
                    // A finished payment can't be "backed" into again.
                    if (success) popUpTo<ReviewPayDestination> { inclusive = true }
                }
            },
        )
    }
    composable<PaymentResultDestination> { entry ->
        PaymentResultScreen(
            success = entry.toRoute<PaymentResultDestination>().success,
            onRetry = { navController.popBackStack() },
            onViewTrips = onViewTrips,
            onGoHome = onGoHome,
        )
    }
}
