package com.jatri.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Soft, very rounded shapes: pill buttons & chips, 24 dp cards, 28 dp sheets. */
object JatriShapes {
    val pill = RoundedCornerShape(percent = 50)
    val button = pill
    val chip = pill
    val circle = CircleShape
    val card = RoundedCornerShape(24.dp)
    val cardSmall = RoundedCornerShape(18.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val field = RoundedCornerShape(18.dp)
}

internal val JatriMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
