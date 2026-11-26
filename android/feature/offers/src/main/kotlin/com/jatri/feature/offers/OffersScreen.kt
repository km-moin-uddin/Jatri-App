package com.jatri.feature.offers

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.data.repository.HomeContentRepository
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.TagChip
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Offer
import com.jatri.core.ui.color
import com.jatri.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OffersViewModel @Inject constructor(repository: HomeContentRepository) : ViewModel() {
    val offers: StateFlow<List<Offer>> = repository.observeOffers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
internal fun OffersRoute(viewModel: OffersViewModel = hiltViewModel()) {
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    OffersScreen(offers)
}

@Composable
fun OffersScreen(offers: List<Offer>) {
    val clipboard = LocalClipboard.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    Scaffold(
        topBar = { JatriTopAppBar(title = stringResource(R.string.offers_title)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (offers.isEmpty()) {
            EmptyState(title = stringResource(R.string.offers_empty), icon = JatriIcons.Offer, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Text(stringResource(R.string.offers_sample_note), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
            }
            items(offers, key = { it.id }) { offer ->
                OfferCard(offer) {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("promo", offer.code)))
                        snackbar.showSnackbar(resources.getString(R.string.offers_copied, offer.code))
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferCard(offer: Offer, onCopy: () -> Unit) {
    val lang = JatriTheme.language
    JatriCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            offer.modes.forEach { TagChip(stringResource(it.labelRes), it.color) }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(offer.title(lang), style = MaterialTheme.typography.titleLarge)
        Text(offer.subtitle(lang), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(offer.code, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.offers_valid_until, DateTimeFormats.longDate(offer.validTo, lang)),
                    style = MaterialTheme.typography.labelMedium,
                    color = JatriTheme.colors.textSecondary,
                )
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.height(Spacing.touchTarget)) {
                Icon(JatriIcons.Copy, contentDescription = null)
                Spacer(Modifier.padding(start = Spacing.xxs))
                Text(stringResource(R.string.offers_copy))
            }
        }
    }
}
