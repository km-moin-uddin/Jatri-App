package com.jatri.feature.notifications.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.feature.notifications.NotificationsRoute
import kotlinx.serialization.Serializable

@Serializable data object NotificationsDestination

fun NavController.navigateToNotifications() = navigate(NotificationsDestination)

/** Inbox from `GET /notifications`; tapping a message opens its ticket. */
fun NavGraphBuilder.notificationsScreen(onBack: () -> Unit, onOpenBooking: (String) -> Unit) {
    composable<NotificationsDestination> {
        NotificationsRoute(onBack = onBack, onOpenBooking = onOpenBooking)
    }
}
