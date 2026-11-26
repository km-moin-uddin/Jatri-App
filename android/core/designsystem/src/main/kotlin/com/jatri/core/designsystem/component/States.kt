package com.jatri.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.R
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing

/** Friendly "nothing here" message with an optional action. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = JatriIcons.Ticket,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(
        icon = icon,
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackground = MaterialTheme.colorScheme.primaryContainer,
        title = title,
        message = message,
        actionText = actionText,
        onAction = onAction,
        modifier = modifier,
    )
}

/** "কিছু একটা ভুল হয়েছে। আবার চেষ্টা করুন।" plus a retry button. */
@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ds_error_title),
    message: String? = stringResource(R.string.ds_error_message),
) {
    StateLayout(
        icon = JatriIcons.Error,
        iconTint = JatriTheme.colors.danger,
        iconBackground = JatriTheme.colors.dangerContainer,
        title = title,
        message = message,
        actionText = stringResource(R.string.ds_retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Full-screen spinner for short waits; prefer [ShimmerList] for lists. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StateLayout(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    message: String?,
    actionText: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = JatriTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            JatriPrimaryButton(text = actionText, onClick = onAction)
        }
    }
}
