package com.jatri.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.R
import com.jatri.core.designsystem.theme.Spacing

/** Rounded skeleton block with a moving highlight. */
fun Modifier.shimmer(corner: Dp = 8.dp): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    clip(RoundedCornerShape(corner)).background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x, 0f),
            end = Offset(x + 400f, 0f),
        ),
    )
}

/** Skeleton shaped like a result card. */
@Composable
fun ResultCardSkeleton(modifier: Modifier = Modifier) {
    JatriCard(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(40.dp).shimmer(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.fillMaxWidth(0.6f).height(14.dp).shimmer(6.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(12.dp).shimmer(6.dp))
            }
            Box(Modifier.width(72.dp).height(24.dp).shimmer(6.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Box(Modifier.fillMaxWidth().height(12.dp).shimmer(6.dp))
    }
}

/** A column of [count] skeleton cards, announced as "Loading" to screen readers. */
@Composable
fun ShimmerList(modifier: Modifier = Modifier, count: Int = 4) {
    val label = stringResource(R.string.ds_loading)
    Column(
        modifier = modifier.padding(Spacing.screen).semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(count) { ResultCardSkeleton() }
    }
}
