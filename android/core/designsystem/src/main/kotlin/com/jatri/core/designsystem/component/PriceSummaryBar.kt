package com.jatri.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing

/**
 * Sticky bottom bar: total on the left ("Total · 2 seats", "৳ 2,500"), CTA button on the right.
 */
@Composable
fun PriceSummaryBar(
    label: String,
    price: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = com.jatri.core.designsystem.theme.JatriShapes.sheet,
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen + 4.dp, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
                Text(price, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(Spacing.md))
            JatriCtaButton(
                text = buttonText,
                onClick = onClick,
                enabled = enabled,
                loading = loading,
                modifier = Modifier.width(184.dp),
            )
        }
    }
}
