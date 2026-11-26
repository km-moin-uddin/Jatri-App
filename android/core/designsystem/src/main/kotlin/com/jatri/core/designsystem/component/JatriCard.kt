package com.jatri.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.Spacing

/** White card with 16 dp corners and a soft 1 dp shadow. Clickable when [onClick] is set. */
@Composable
fun JatriCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = Spacing.cardElevation)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = JatriShapes.card, colors = colors, elevation = elevation) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = JatriShapes.card, colors = colors, elevation = elevation) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
