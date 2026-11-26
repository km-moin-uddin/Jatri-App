package com.jatri.core.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.component.JatriBottomSheet
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.ui.R

/**
 * Full-height sheet to pick an airport / bus city / station. Search matches English and Bangla
 * names, city and code (so "cox", "কক্স" and "CXB" all find Cox's Bazar).
 */
@Composable
fun CityPickerSheet(
    title: String,
    locations: List<Location>,
    onSelect: (Location) -> Unit,
    onDismiss: () -> Unit,
    recent: List<Location> = emptyList(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    val language = JatriTheme.language
    val filtered = remember(query, locations) { filterLocations(locations, query) }

    JatriBottomSheet(title = title, onDismiss = onDismiss, fullHeight = true) {
        JatriTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.ui_picker_search_hint),
            leadingIcon = { Icon(JatriIcons.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(JatriIcons.Close, contentDescription = stringResource(com.jatri.core.designsystem.R.string.ds_close))
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        LazyColumn(Modifier.fillMaxWidth().imePadding()) {
            if (query.isBlank()) {
                if (recent.isNotEmpty()) {
                    item { GroupHeader(stringResource(R.string.ui_picker_recent)) }
                    items(recent, key = { "r_" + it.id }) { LocationRow(it, language) { onSelect(it) } }
                }
                val popular = locations.filter { it.isPopular }
                if (popular.isNotEmpty()) {
                    item { GroupHeader(stringResource(R.string.ui_picker_popular)) }
                    items(popular, key = { "p_" + it.id }) { LocationRow(it, language) { onSelect(it) } }
                }
                item { GroupHeader(stringResource(R.string.ui_picker_all)) }
                items(locations.sortedBy { it.name(language) }, key = { "a_" + it.id }) {
                    LocationRow(it, language) { onSelect(it) }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.ui_picker_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = JatriTheme.colors.textSecondary,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                }
            } else {
                items(filtered, key = { it.id }) { LocationRow(it, language) { onSelect(it) } }
            }
        }
    }
}

internal fun filterLocations(locations: List<Location>, query: String): List<Location> {
    val q = query.trim()
    if (q.isEmpty()) return locations
    return locations.filter {
        it.code.contains(q, ignoreCase = true) ||
            it.nameEn.contains(q, ignoreCase = true) ||
            it.cityEn.contains(q, ignoreCase = true) ||
            it.nameBn.contains(q) ||
            it.cityBn.contains(q)
    }.sortedByDescending { it.code.equals(q, ignoreCase = true) }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = JatriTheme.colors.textSecondary,
        modifier = Modifier
            .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = Spacing.xxs)
            .semantics { heading() },
    )
}

@Composable
private fun LocationRow(location: Location, language: AppLanguage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (location.type == LocationType.AIRPORT) {
                Text(location.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Icon(JatriIcons.Location, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(location.city(language), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                location.name(language),
                style = MaterialTheme.typography.bodySmall,
                color = JatriTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
