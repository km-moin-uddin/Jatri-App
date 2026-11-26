package com.jatri.core.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jatri.core.designsystem.component.JatriBottomSheet
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.PassengerCounter
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.CabinClass
import com.jatri.core.model.CoachType
import com.jatri.core.model.PassengerCount
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TravelMode
import com.jatri.core.ui.R
import com.jatri.core.ui.label

/**
 * Mode-aware options sheet:
 * - Flight: adults/children/infants + cabin class
 * - Bus: coach type (seats are picked on the seat map)
 * - Train: seat class + adults/children (max 4)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassengerClassSheet(
    query: SearchQuery,
    onChange: (SearchQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (query.mode) {
        TravelMode.BUS -> stringResource(R.string.ui_coach_type)
        else -> stringResource(R.string.ui_pax_title)
    }
    JatriBottomSheet(title = title, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.screen).navigationBarsPadding()) {
            when (query.mode) {
                TravelMode.FLIGHT -> {
                    FlightPassengers(query.passengers) { onChange(query.copy(passengers = it)) }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = JatriTheme.colors.divider)
                    SheetLabel(stringResource(R.string.ui_class))
                    ChoiceChips(CabinClass.entries, query.cabinClass, { it.label() }) { onChange(query.copy(cabinClass = it)) }
                    Rules(stringResource(R.string.ui_pax_rules))
                }
                TravelMode.BUS -> {
                    ChoiceChips(CoachType.entries, query.coachType, { it.label() }) { onChange(query.copy(coachType = it)) }
                }
                TravelMode.TRAIN -> {
                    SheetLabel(stringResource(R.string.ui_class))
                    ChoiceChips(TrainClass.entries, query.trainClass, { it.label() }) { onChange(query.copy(trainClass = it)) }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = JatriTheme.colors.divider)
                    TrainPassengers(query.passengers) { onChange(query.copy(passengers = it)) }
                    Rules(stringResource(R.string.ui_train_pax_rules))
                }
            }
            Spacer(Modifier.height(Spacing.md))
            JatriCtaButton(text = stringResource(R.string.ui_done), onClick = onDismiss)
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun FlightPassengers(p: PassengerCount, onChange: (PassengerCount) -> Unit) {
    val roomLeft = PassengerCount.MAX_TOTAL - p.total
    PassengerCounter(
        label = stringResource(R.string.ui_pax_adults),
        description = stringResource(R.string.ui_pax_adults_desc),
        count = p.adults,
        min = 1,
        max = p.adults + roomLeft,
        // Infants can never outnumber adults.
        onCountChange = { onChange(p.copy(adults = it, infants = minOf(p.infants, it))) },
    )
    PassengerCounter(
        label = stringResource(R.string.ui_pax_children),
        description = stringResource(R.string.ui_pax_children_desc),
        count = p.children,
        max = p.children + roomLeft,
        onCountChange = { onChange(p.copy(children = it)) },
    )
    PassengerCounter(
        label = stringResource(R.string.ui_pax_infants),
        description = stringResource(R.string.ui_pax_infants_desc),
        count = p.infants,
        max = minOf(p.adults, p.infants + roomLeft),
        onCountChange = { onChange(p.copy(infants = it)) },
    )
}

private const val TRAIN_MAX_PASSENGERS = 4

@Composable
private fun TrainPassengers(p: PassengerCount, onChange: (PassengerCount) -> Unit) {
    val roomLeft = TRAIN_MAX_PASSENGERS - (p.adults + p.children)
    PassengerCounter(
        label = stringResource(R.string.ui_pax_adults),
        description = stringResource(R.string.ui_pax_adults_desc),
        count = p.adults,
        min = 1,
        max = p.adults + roomLeft,
        onCountChange = { onChange(p.copy(adults = it, infants = 0)) },
    )
    PassengerCounter(
        label = stringResource(R.string.ui_pax_children),
        description = stringResource(R.string.ui_pax_children_desc),
        count = p.children,
        max = p.children + roomLeft,
        onCountChange = { onChange(p.copy(children = it, infants = 0)) },
    )
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.xs))
}

@Composable
private fun Rules(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = JatriTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { option -> PillChip(text = label(option), selected = option == selected, onClick = { onSelect(option) }) }
    }
}
