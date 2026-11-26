package com.jatri.core.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.TravelMode
import com.jatri.core.ui.R
import com.jatri.core.ui.label
import com.jatri.core.ui.selectPlaceRes

/**
 * The home search card: From ⇅ To, date, passengers/class and the orange Search button.
 * Mode pills ([TravelModeTabs]) sit above it on the Home screen. Stateless.
 */
@Composable
fun SearchCard(
    query: SearchQuery,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onSwap: () -> Unit,
    onDateClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    val language = JatriTheme.language
    JatriCard(modifier = modifier.fillMaxWidth()) {
        Box {
            Column {
                LocationField(
                    label = stringResource(R.string.ui_from),
                    location = query.from,
                    placeholder = stringResource(query.mode.selectPlaceRes),
                    onClick = onFromClick,
                )
                HorizontalDivider(color = JatriTheme.colors.divider, modifier = Modifier.padding(end = 56.dp))
                LocationField(
                    label = stringResource(R.string.ui_to),
                    location = query.to,
                    placeholder = stringResource(query.mode.selectPlaceRes),
                    onClick = onToClick,
                )
            }
            SwapButton(onSwap = onSwap, modifier = Modifier.align(Alignment.CenterEnd))
        }

        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OptionField(
                label = stringResource(R.string.ui_journey_date),
                value = DateTimeFormats.shortDate(query.date, language),
                icon = JatriIcons.Calendar,
                onClick = onDateClick,
                modifier = Modifier.weight(1f),
            )
            OptionField(
                label = optionLabel(query.mode),
                value = optionValue(query),
                icon = if (query.mode == TravelMode.BUS) JatriIcons.Bus else JatriIcons.Group,
                onClick = onOptionsClick,
                modifier = Modifier.weight(1f),
            )
        }

        if (errorText != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(Spacing.md))
        JatriCtaButton(
            text = stringResource(R.string.ui_search_tickets),
            onClick = onSearch,
            leadingIcon = JatriIcons.Search,
        )
    }
}

@Composable
private fun optionLabel(mode: TravelMode): String = when (mode) {
    TravelMode.FLIGHT -> stringResource(R.string.ui_passengers) + " · " + stringResource(R.string.ui_class)
    TravelMode.BUS -> stringResource(R.string.ui_coach_type)
    TravelMode.TRAIN -> stringResource(R.string.ui_class) + " · " + stringResource(R.string.ui_passengers)
}

@Composable
private fun optionValue(query: SearchQuery): String {
    val language = JatriTheme.language
    val total = query.passengers.total
    val pax = pluralStringResource(R.plurals.ui_passenger_count, total, total.localized(language))
    return when (query.mode) {
        TravelMode.FLIGHT -> "$pax, ${query.cabinClass.label()}"
        TravelMode.BUS -> query.coachType.label()
        TravelMode.TRAIN -> "${query.trainClass.label()}, $pax"
    }
}

@Composable
private fun LocationField(label: String, location: Location?, placeholder: String, onClick: () -> Unit) {
    val language = JatriTheme.language
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(JatriShapes.field)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm)
            .padding(end = 56.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
        if (location == null) {
            Text(
                placeholder,
                style = MaterialTheme.typography.titleLarge,
                color = JatriTheme.colors.textSecondary,
            )
        } else {
            Text(
                text = location.city(language),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = if (location.type == LocationType.AIRPORT) {
                "${location.code} · ${location.name(language)}"
            } else {
                location.name(language)
            }
            if (sub != location.city(language)) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = JatriTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SwapButton(onSwap: () -> Unit, modifier: Modifier = Modifier) {
    var turns by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(turns * 180f, tween(250), label = "swapRotation")
    OutlinedIconButton(
        onClick = {
            turns += 1f
            onSwap()
        },
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.size(Spacing.touchTarget),
    ) {
        Icon(
            imageVector = JatriIcons.Swap,
            contentDescription = stringResource(R.string.ui_swap),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
private fun OptionField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = JatriShapes.field,
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = JatriTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
