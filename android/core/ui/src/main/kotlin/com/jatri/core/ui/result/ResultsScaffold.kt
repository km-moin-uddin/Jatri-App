package com.jatri.core.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.model.AppError
import com.jatri.core.ui.text
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.component.ShimmerList
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.ui.R
import com.jatri.core.ui.placeholder.PhaseBanner
import java.time.LocalDate

enum class ResultSort { CHEAPEST, FASTEST, EARLIEST }

/**
 * Shared frame for flight/bus/train result lists: round back button + centred route title, a
 * strip of date pills, "Available trips" with sort pills, shimmer while loading, the friendly
 * empty state, and the "coming in Phase N" banner while data is mocked.
 */
@Composable
fun ResultsScaffold(
    title: String,
    subtitle: String,
    loading: Boolean,
    count: Int,
    sort: ResultSort,
    onSortChange: (ResultSort) -> Unit,
    onBack: () -> Unit,
    phase: String?,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    error: AppError? = null,
    onRetry: () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = title, subtitle = subtitle, onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DateStrip(date = date, today = today, onDateChange = onDateChange)
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ui_available_trips), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (!loading && count > 0) {
                    Text(
                        stringResource(R.string.ui_results_count, count.localized(JatriTheme.language)),
                        style = MaterialTheme.typography.labelMedium,
                        color = JatriTheme.colors.textSecondary,
                    )
                }
            }
            SortPills(sort, onSortChange)
            when {
                loading -> ShimmerList()
                error != null -> ErrorState(onRetry = onRetry, message = error.text())
                count == 0 -> EmptyState(
                    title = stringResource(R.string.ui_no_results_title),
                    message = stringResource(R.string.ui_no_results_body),
                    actionText = stringResource(R.string.ui_change_search),
                    onAction = onBack,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (phase != null) item { PhaseBanner(phase) }
                    content()
                    item { Spacer(Modifier.height(Spacing.lg)) }
                }
            }
        }
    }
}

/** Five day pills around the chosen date (never before today): "Thu 27". */
@Composable
private fun DateStrip(date: LocalDate, today: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val lang = JatriTheme.language
    val first = maxOf(today, date.minusDays(2))
    val days = (0L until 5L).map { first.plusDays(it) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(days, key = { it.toEpochDay() }) { day ->
            PillChip(
                text = "${DateTimeFormats.dayOfWeek(day, lang)} ${DateTimeFormats.dayOfMonth(day, lang)}",
                selected = day == date,
                onClick = { onDateChange(day) },
            )
        }
    }
}

@Composable
private fun SortPills(sort: ResultSort, onSortChange: (ResultSort) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(ResultSort.entries) { option ->
            PillChip(
                text = stringResource(
                    when (option) {
                        ResultSort.CHEAPEST -> R.string.ui_sort_cheapest
                        ResultSort.FASTEST -> R.string.ui_sort_fastest
                        ResultSort.EARLIEST -> R.string.ui_sort_earliest
                    },
                ),
                selected = option == sort,
                onClick = { onSortChange(option) },
            )
        }
    }
}
