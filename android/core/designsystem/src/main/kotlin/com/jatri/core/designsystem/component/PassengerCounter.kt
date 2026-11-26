package com.jatri.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.R
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing

/** "Adults (12+ years)   [-] 1 [+]" row. */
@Composable
fun PassengerCounter(
    label: String,
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    min: Int = 0,
    max: Int = 9,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
            }
        }
        CounterButton(
            onClick = { onCountChange(count - 1) },
            enabled = count > min,
            contentDescription = stringResource(R.string.ds_decrease, label),
            plus = false,
        )
        Text(
            text = count.localized(JatriTheme.language),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        CounterButton(
            onClick = { onCountChange(count + 1) },
            enabled = count < max,
            contentDescription = stringResource(R.string.ds_increase, label),
            plus = true,
        )
    }
}

@Composable
private fun CounterButton(onClick: () -> Unit, enabled: Boolean, contentDescription: String, plus: Boolean) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary else JatriTheme.colors.divider),
        modifier = Modifier.size(Spacing.touchTarget),
    ) {
        Icon(
            imageVector = if (plus) JatriIcons.Add else JatriIcons.Remove,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else JatriTheme.colors.divider,
        )
    }
}
