package com.jatri.feature.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.JatriTextButton
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.theme.JatriPalette
import com.jatri.core.ui.GradientIllustration
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.statusBarsPadding
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppLanguage
import kotlinx.coroutines.launch

private data class Slide(val icon: ImageVector, val color: Color, @StringRes val title: Int, @StringRes val body: Int)

private val slides = listOf(
    Slide(JatriIcons.Ticket, JatriPalette.OceanTeal, R.string.auth_onb1_title, R.string.auth_onb1_body),
    Slide(JatriIcons.Lock, JatriPalette.SkyBlue, R.string.auth_onb2_title, R.string.auth_onb2_body),
    Slide(JatriIcons.Payments, JatriPalette.LeafGreen, R.string.auth_onb3_title, R.string.auth_onb3_body),
)

@Composable
internal fun OnboardingRoute(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    OnboardingScreen(
        language = language,
        onLanguageChange = viewModel::setLanguage,
        onFinish = { viewModel.finish(onFinished) },
    )
}

/** 3 slides, language choice on the first one, Skip on every slide. */
@Composable
fun OnboardingScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onFinish: () -> Unit,
    initialPage: Int = 0,
) {
    val pager = rememberPagerState(initialPage = initialPage) { slides.size }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == slides.lastIndex
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), horizontalArrangement = Arrangement.End) {
            if (!isLast) JatriTextButton(text = stringResource(R.string.auth_skip), onClick = onFinish)
            else Spacer(Modifier.height(Spacing.touchTarget))
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val slide = slides[page]
            Column(
                Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.Center,
            ) {
                GradientIllustration(
                    color = slide.color,
                    icon = slide.icon,
                    iconSize = 96.dp,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )
                Spacer(Modifier.height(Spacing.xl))
                Text(stringResource(slide.title), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(slide.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = JatriTheme.colors.textSecondary,
                )
                if (page == 0) {
                    Spacer(Modifier.height(Spacing.xl))
                    LanguageChooser(language, onLanguageChange)
                }
            }
        }
        PageDots(count = slides.size, current = pager.currentPage)
        Spacer(Modifier.height(Spacing.lg))
        JatriCtaButton(
            text = stringResource(if (isLast) R.string.auth_get_started else R.string.auth_next),
            onClick = {
                if (isLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun LanguageChooser(language: AppLanguage, onChange: (AppLanguage) -> Unit) {
    Column {
        Text(stringResource(R.string.auth_choose_language), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Each language is written in itself so users can find theirs whatever is on screen.
            LanguageChip("বাংলা", language == AppLanguage.BANGLA) { onChange(AppLanguage.BANGLA) }
            LanguageChip("English", language == AppLanguage.ENGLISH) { onChange(AppLanguage.ENGLISH) }
        }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    PillChip(text = label, selected = selected, onClick = onClick, icon = if (selected) JatriIcons.Check else null)
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val lang = JatriTheme.language
    val description = stringResource(R.string.auth_page_indicator, (current + 1).localized(lang), count.localized(lang))
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (i == current) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else JatriTheme.colors.divider),
            )
        }
    }
}
