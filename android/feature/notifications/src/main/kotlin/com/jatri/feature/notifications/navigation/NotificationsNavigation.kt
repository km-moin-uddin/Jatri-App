package com.jatri.feature.notifications.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.feature.notifications.R
import kotlinx.serialization.Serializable

@Serializable data object NotificationsDestination

fun NavController.navigateToNotifications() = navigate(NotificationsDestination)

/** Inbox. Push via FCM and the list from `GET /notifications` arrive in Phase 7. */
fun NavGraphBuilder.notificationsScreen(onBack: () -> Unit) {
    composable<NotificationsDestination> {
        Scaffold(topBar = { JatriTopAppBar(title = stringResource(R.string.notif_title), onBack = onBack) }) { padding ->
            EmptyState(
                title = stringResource(R.string.notif_empty),
                message = stringResource(R.string.notif_empty_body),
                icon = JatriIcons.Notifications,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
