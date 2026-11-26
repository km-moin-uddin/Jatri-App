package com.jatri.core.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.ui.R

/**
 * Shared frame for screens whose real implementation comes in a later phase. Shows a clear
 * "Coming in Phase N · sample data" banner so nobody mistakes mock content for real data.
 */
@Composable
fun PhasePlaceholderScreen(
    title: String,
    phase: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = { JatriTopAppBar(title = title, subtitle = subtitle, onBack = onBack) },
        bottomBar = {
            if (primaryActionText != null && onPrimaryAction != null) {
                Surface(shadowElevation = 8.dp) {
                    JatriCtaButton(
                        text = primaryActionText,
                        onClick = onPrimaryAction,
                        modifier = Modifier.navigationBarsPadding().padding(Spacing.screen),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(Spacing.screen)),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PhaseBanner(phase)
            content()
        }
    }
}

@Composable
fun PhaseBanner(phase: String, modifier: Modifier = Modifier, body: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = JatriShapes.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.Top) {
            Icon(JatriIcons.Time, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
            Column {
                Text(stringResource(R.string.ui_placeholder_title, phase), style = MaterialTheme.typography.titleSmall)
                Text(body ?: stringResource(R.string.ui_placeholder_body), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
