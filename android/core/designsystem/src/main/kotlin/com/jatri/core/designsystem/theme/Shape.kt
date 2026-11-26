package com.jatri.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii: buttons 12 · cards 16 · chips 20 · bottom sheets 24 (top corners only). */
object JatriShapes {
    val button = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val field = RoundedCornerShape(12.dp)
}

internal val JatriMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
