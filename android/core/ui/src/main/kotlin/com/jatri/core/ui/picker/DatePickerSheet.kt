package com.jatri.core.ui.picker

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jatri.core.designsystem.component.JatriBottomSheet
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Calendar in a bottom sheet. Only dates from [minDate] to [maxDate] can be picked
 * (e.g. train tickets open 10 days ahead, flights up to a year).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate = LocalDate.now(),
    maxDate: LocalDate = minDate.plusDays(365),
    title: String = stringResource(R.string.ui_date_title),
) {
    val selectable = remember(minDate, maxDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return !date.isBefore(minDate) && !date.isAfter(maxDate)
            }

            override fun isSelectableYear(year: Int): Boolean = year in minDate.year..maxDate.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcMillis(),
        selectableDates = selectable,
    )
    JatriBottomSheet(title = title, onDismiss = onDismiss) {
        DatePicker(
            state = state,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
        JatriCtaButton(
            text = stringResource(R.string.ui_done),
            onClick = {
                state.selectedDateMillis?.let {
                    onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            },
            modifier = Modifier.padding(Spacing.screen),
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
