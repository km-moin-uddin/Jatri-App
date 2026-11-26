package com.jatri.feature.payment.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jatri.feature.payment.PaymentResultScreen
import com.jatri.feature.payment.ReviewPayRoute
import kotlinx.serialization.Serializable

/** [bookingId] null = flight/train preview with sample numbers (until Phases 5–6). */
@Serializable data class ReviewPayDestination(val bookingId: String? = null)
@Serializable data class PaymentResultDestination(val success: Boolean)

fun NavController.navigateToReviewPay(bookingId: String? = null) = navigate(ReviewPayDestination(bookingId))

fun NavGraphBuilder.paymentScreens(
    navController: NavController,
    onViewTrips: () -> Unit,
    onGoHome: () -> Unit,
) {
    composable<ReviewPayDestination> {
        ReviewPayRoute(
            onBack = { navController.popBackStack() },
            onPaid = { success ->
                navController.navigate(PaymentResultDestination(success)) {
                    // A finished payment can't be "backed" into again.
                    if (success) popUpTo<ReviewPayDestination> { inclusive = true }
                }
            },
            onHoldGone = onGoHome,
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
