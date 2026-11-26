package com.jatri.core.ui.search

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.TravelMode
import com.jatri.core.ui.color
import com.jatri.core.ui.icon
import com.jatri.core.ui.labelRes

/**
 * Row of mode pills. The selected mode is a black pill with its icon in a white circle and its
 * name; the others are round icon buttons (with their name for screen readers).
 */
@Composable
fun TravelModeTabs(
    selected: TravelMode,
    onSelect: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TravelMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val label = stringResource(mode.labelRes)
            Surface(
                shape = JatriShapes.pill,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (isSelected) null else BorderStroke(1.dp, JatriTheme.colors.cardBorder),
                modifier = Modifier
                    .height(56.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(mode) })
                    .semantics { contentDescription = label }
                    .animateContentSize(tween(250)),
            ) {
                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,
                                JatriShapes.circle,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(mode.icon, contentDescription = null, tint = mode.color, modifier = Modifier.size(24.dp))
                    }
                    if (isSelected) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(Spacing.md))
                    }
                }
            }
        }
    }
}
