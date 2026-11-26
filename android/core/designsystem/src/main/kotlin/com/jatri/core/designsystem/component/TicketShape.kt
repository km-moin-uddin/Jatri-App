package com.jatri.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing

/**
 * Rounded rectangle with a half-circle notch cut into each side, like a paper ticket.
 * The notches sit at [notchOffsetFraction] of the height (0.5 = middle).
 */
class TicketShape(
    private val cornerRadius: Dp = 24.dp,
    private val notchRadius: Dp = 12.dp,
    private val notchOffsetFraction: Float = 0.5f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { cornerRadius.toPx() }
        val notch = with(density) { notchRadius.toPx() }
        val cy = size.height * notchOffsetFraction
        val body = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(corner)))
        }
        val notches = Path().apply {
            addOval(Rect(center = Offset(0f, cy), radius = notch))
            addOval(Rect(center = Offset(size.width, cy), radius = notch))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, notches))
    }
}

/**
 * Ticket-style card: [top] part, a dashed tear line level with the notches, then [bottom].
 * Used for e-tickets in My Trips and on the payment success screen.
 */
@Composable
fun TicketCard(
    modifier: Modifier = Modifier,
    notchOffsetFraction: Float = 0.62f,
    top: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = TicketShape(notchOffsetFraction = notchOffsetFraction),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), content = top)
        DashedDivider(Modifier.padding(horizontal = Spacing.lg))
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), content = bottom)
    }
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val color = JatriTheme.colors.divider
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
    }
}
