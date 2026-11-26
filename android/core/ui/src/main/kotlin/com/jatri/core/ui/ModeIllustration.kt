package com.jatri.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.model.TravelMode

/**
 * Decorative artwork used instead of photos: a soft gradient in the mode's colour, two light
 * "sun" circles, a dashed route arc and a big white mode icon. Purely decorative (no semantics).
 */
@Composable
fun ModeIllustration(
    mode: TravelMode,
    modifier: Modifier = Modifier,
    shape: Shape = JatriShapes.card,
    iconSize: Dp = 72.dp,
) = GradientIllustration(color = mode.color, icon = mode.icon, modifier = modifier, shape = shape, iconSize = iconSize)

/** Same artwork for any colour + icon (onboarding slides, empty states). */
@Composable
fun GradientIllustration(
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = JatriShapes.card,
    iconSize: Dp = 72.dp,
) {
    val base = color
    val light = lerp(base, Color.White, 0.45f)
    val deep = lerp(base, Color.Black, 0.25f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(light, base, deep), start = Offset.Zero, end = Offset.Infinite)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Color.White.copy(alpha = 0.18f), radius = h * 0.55f, center = Offset(w * 0.88f, h * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = h * 0.40f, center = Offset(w * 0.05f, h * 1.0f))
            val arc = Path().apply {
                moveTo(w * 0.08f, h * 0.82f)
                cubicTo(w * 0.30f, h * 0.20f, w * 0.70f, h * 0.20f, w * 0.92f, h * 0.70f)
            }
            drawPath(
                arc,
                color = Color.White.copy(alpha = 0.55f),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
            )
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(w * 0.08f, h * 0.82f))
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(w * 0.92f, h * 0.70f))
        }
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}
