package com.jatri.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.JatriTheme

enum class StatusTone { Success, Warning, Danger, Info, Neutral }

/** Small rounded label: "Confirmed", "Pending", "Cancelled", "Refundable", "3 seats left". */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val (bg, fg) = toneColors(tone)
    Row(
        modifier = modifier
            .background(bg, JatriShapes.chip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

/** Coloured tag for a transport mode or similar ("Flight", "AC"). */
@Composable
fun TagChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), JatriShapes.chip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun toneColors(tone: StatusTone): Pair<Color, Color> {
    val c = JatriTheme.colors
    return when (tone) {
        StatusTone.Success -> c.successContainer to c.success
        StatusTone.Warning -> c.warningContainer to c.warning
        StatusTone.Danger -> c.dangerContainer to c.danger
        StatusTone.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}
