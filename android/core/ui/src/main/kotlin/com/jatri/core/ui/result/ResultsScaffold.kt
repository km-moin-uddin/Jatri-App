package com.jatri.core.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.ShimmerList
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.ui.R
import com.jatri.core.ui.placeholder.PhaseBanner

enum class ResultSort { CHEAPEST, FASTEST, EARLIEST }

/**
 * Shared frame for flight/bus/train result lists: summary top bar, sort chips, shimmer while
 * loading, the friendly empty state, and the "coming in Phase N" banner while data is mocked.
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
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(modifier = modifier, topBar = { JatriTopAppBar(title = title, subtitle = subtitle, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SortChips(sort, onSortChange)
            when {
                loading -> ShimmerList()
                count == 0 -> EmptyState(
                    title = stringResource(R.string.ui_no_results_title),
                    message = stringResource(R.string.ui_no_results_body),
                    actionText = stringResource(R.string.ui_change_search),
                    onAction = onBack,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (phase != null) item { PhaseBanner(phase) }
                    item {
                        Text(
                            stringResource(R.string.ui_results_count, count.localized(JatriTheme.language)),
                            style = MaterialTheme.typography.labelLarge,
                            color = JatriTheme.colors.textSecondary,
                        )
                    }
                    content()
                    item { Spacer(Modifier.height(Spacing.lg)) }
                }
            }
        }
    }
}

@Composable
private fun SortChips(sort: ResultSort, onSortChange: (ResultSort) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(ResultSort.entries) { option ->
            FilterChip(
                selected = option == sort,
                onClick = { onSortChange(option) },
                label = {
                    Text(
                        stringResource(
                            when (option) {
                                ResultSort.CHEAPEST -> R.string.ui_sort_cheapest
                                ResultSort.FASTEST -> R.string.ui_sort_fastest
                                ResultSort.EARLIEST -> R.string.ui_sort_earliest
                            },
                        ),
                    )
                },
                shape = JatriShapes.chip,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
