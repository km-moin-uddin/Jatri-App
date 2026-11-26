package com.jatri.feature.payment.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jatri.feature.payment.PaymentProcessingRoute
import com.jatri.feature.payment.PaymentResultRoute
import com.jatri.feature.payment.ReviewPayRoute
import kotlinx.serialization.Serializable

/** [bookingId] null = flight/train preview with sample numbers (until Phases 5–6). */
@Serializable data class ReviewPayDestination(val bookingId: String? = null)

/** Waits for the server to confirm a payment. [demo] = in-app demo gateway instead of a browser. */
@Serializable data class PaymentProcessingDestination(val paymentId: String, val demo: Boolean, val amountPaisa: Long)

/** [bookingId] set = show the real e-ticket. */
@Serializable data class PaymentResultDestination(val success: Boolean, val bookingId: String? = null)

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
                // Flight/train preview only: no real payment yet.
                navController.navigate(PaymentResultDestination(success)) {
                    if (success) popUpTo<ReviewPayDestination> { inclusive = true }
                }
            },
            onPaymentStarted = { start, demo ->
                navController.navigate(PaymentProcessingDestination(start.paymentId, demo, start.amount.paisa))
            },
            onHoldGone = onGoHome,
        )
    }
    composable<PaymentProcessingDestination> {
        PaymentProcessingRoute(
            onTicketed = { bookingId ->
                navController.navigate(PaymentResultDestination(success = true, bookingId = bookingId)) {
                    // Paid: there is no way "back" into the payment screens.
                    popUpTo<ReviewPayDestination> { inclusive = true }
                }
            },
            onFailed = {
                navController.navigate(PaymentResultDestination(success = false)) {
                    popUpTo<PaymentProcessingDestination> { inclusive = true }
                }
            },
            onGoHome = onGoHome,
            onViewTrips = onViewTrips,
        )
    }
    composable<PaymentResultDestination> { entry ->
        val args = entry.toRoute<PaymentResultDestination>()
        PaymentResultRoute(
            success = args.success,
            onRetry = { navController.popBackStack() },
            onViewTrips = onViewTrips,
            onGoHome = onGoHome,
        )
    }
}
