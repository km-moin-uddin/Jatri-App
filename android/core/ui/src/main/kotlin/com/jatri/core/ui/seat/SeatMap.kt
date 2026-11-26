package com.jatri.core.ui.seat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatLayout
import com.jatri.core.model.SeatState
import com.jatri.core.ui.R

private val SeatSize = 44.dp
private val SeatShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
private val LadiesColor = Color(0xFFD9468F)

/**
 * Bus seat map (2+2 or 2+1 — whatever [layout] describes). Booked seats can't be tapped; once
 * [maxSelectable] seats are chosen, other available seats are disabled. Seat codes (A1, B2) are
 * printed on the bus, so they stay in Latin letters and ASCII digits in both languages.
 */
@Composable
fun SeatMap(
    layout: SeatLayout,
    selected: Set<String>,
    onSeatToggle: (Seat) -> Unit,
    modifier: Modifier = Modifier,
    maxSelectable: Int = SeatLayout.MAX_SEATS_PER_BOOKING,
) {
    JatriCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(JatriIcons.Support, contentDescription = null, tint = JatriTheme.colors.textSecondary)
                Text(stringResource(R.string.ui_seat_driver), style = MaterialTheme.typography.labelSmall, color = JatriTheme.colors.textSecondary)
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            layout.rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    row.forEach { seat ->
                        if (seat == null) {
                            Spacer(Modifier.width(SeatSize * 0.75f))
                        } else {
                            val isSelected = seat.number in selected
                            val limitReached = selected.size >= maxSelectable && !isSelected
                            val taken = seat.state == SeatState.BOOKED || seat.state == SeatState.HELD
                            SeatCell(seat, isSelected, enabled = !taken && !limitReached) { onSeatToggle(seat) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(Spacing.md))
        SeatLegend()
    }
}

@Composable
private fun SeatCell(seat: Seat, isSelected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val (container, content, border) = when {
        isSelected -> Triple(primary, MaterialTheme.colorScheme.onPrimary, primary)
        seat.state == SeatState.BOOKED || seat.state == SeatState.HELD ->
            Triple(MaterialTheme.colorScheme.surfaceVariant, JatriTheme.colors.textSecondary, Color.Transparent)
        seat.state == SeatState.LADIES -> Triple(LadiesColor.copy(alpha = 0.10f), LadiesColor, LadiesColor)
        else -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.outline)
    }
    val stateLabel = stringResource(
        when {
            isSelected -> R.string.ui_seat_selected
            seat.state == SeatState.BOOKED || seat.state == SeatState.HELD -> R.string.ui_seat_booked
            seat.state == SeatState.LADIES -> R.string.ui_seat_ladies
            else -> R.string.ui_seat_available
        },
    )
    val description = stringResource(R.string.ui_seat_description, seat.number, stateLabel)
    Surface(
        color = container,
        contentColor = content,
        shape = SeatShape,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .size(SeatSize)
            .toggleable(value = isSelected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(seat.number, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LegendItem(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline, stringResource(R.string.ui_seat_available))
        LegendItem(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary, stringResource(R.string.ui_seat_selected))
        LegendItem(MaterialTheme.colorScheme.surfaceVariant, Color.Transparent, stringResource(R.string.ui_seat_booked))
        LegendItem(LadiesColor.copy(alpha = 0.10f), LadiesColor, stringResource(R.string.ui_seat_ladies))
    }
}

@Composable
private fun LegendItem(fill: Color, border: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).border(1.dp, border, SeatShape).padding(1.dp)) {
            Surface(color = fill, shape = SeatShape, modifier = Modifier.size(14.dp)) {}
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
    }
}
