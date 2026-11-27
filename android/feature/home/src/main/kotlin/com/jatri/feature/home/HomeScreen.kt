package com.jatri.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.format
import com.jatri.core.designsystem.component.CircleIconButton
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriSmallButton
import com.jatri.core.designsystem.component.SectionHeader
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriPalette
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Offer
import com.jatri.core.model.PopularRoute
import com.jatri.core.model.RecentSearch
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.TravelMode
import com.jatri.core.ui.color
import com.jatri.core.ui.icon
import com.jatri.core.ui.picker.CityPickerSheet
import com.jatri.core.ui.picker.DatePickerSheet
import com.jatri.core.ui.picker.PassengerClassSheet
import com.jatri.core.ui.search.FlightSearchActions
import com.jatri.core.ui.ModeIllustration
import com.jatri.core.ui.search.SearchCard
import com.jatri.core.ui.search.TravelModeTabs
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.jatri.core.ui.selectPlaceRes
import java.time.LocalTime

@Composable
internal fun HomeRoute(
    onSearch: (SearchQuery) -> Unit,
    onNotificationsClick: () -> Unit,
    onSeeAllOffers: () -> Unit,
    onAccountClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshUnread()
        onPauseOrDispose { }
    }
    HomeScreen(
        state = state,
        actions = HomeActions(
            onModeChange = viewModel::selectMode,
            onOpenSheet = viewModel::openSheet,
            onSwap = viewModel::swap,
            onSearch = { viewModel.search(onSearch) },
            flight = FlightSearchActions(
                onTripTypeChange = viewModel::setTripType,
                onReturnDateClick = { viewModel.openSheet(HomeSheet.RETURN_DATE) },
                onLegFromClick = { viewModel.openLegSheet(HomeSheet.FROM, it) },
                onLegToClick = { viewModel.openLegSheet(HomeSheet.TO, it) },
                onLegDateClick = { viewModel.openLegSheet(HomeSheet.DATE, it) },
                onAddLeg = viewModel::addLeg,
                onRemoveLeg = viewModel::removeLeg,
            ),
            onRecentClick = viewModel::applyRecent,
            onPopularClick = viewModel::applyPopular,
            onNotificationsClick = onNotificationsClick,
            onSeeAllOffers = onSeeAllOffers,
            onAccountClick = onAccountClick,
        ),
    )
    when (state.sheet) {
        HomeSheet.FROM, HomeSheet.TO -> CityPickerSheet(
            title = stringResource(state.query.mode.selectPlaceRes),
            locations = state.locations,
            recent = state.recentSearches.flatMap { listOf(it.from, it.to) }.distinctBy { it.id }.take(4),
            onSelect = if (state.sheet == HomeSheet.FROM) viewModel::setFrom else viewModel::setTo,
            onDismiss = viewModel::closeSheet,
        )
        HomeSheet.DATE, HomeSheet.RETURN_DATE -> DatePickerSheet(
            selected = state.sheetDate,
            onSelect = viewModel::setDate,
            onDismiss = viewModel::closeSheet,
            minDate = state.sheetMinDate,
            // Bangladesh Railway sells tickets 10 days ahead; flights up to a year.
            maxDate = when (state.query.mode) {
                TravelMode.TRAIN -> state.today.plusDays(10)
                TravelMode.BUS -> state.today.plusDays(30)
                TravelMode.FLIGHT -> state.today.plusDays(365)
            },
        )
        HomeSheet.OPTIONS -> PassengerClassSheet(
            query = state.query,
            onChange = viewModel::updateQuery,
            onDismiss = viewModel::closeSheet,
        )
        HomeSheet.NONE -> Unit
    }
}

/** All callbacks of the Home screen, grouped so the stateless screen stays readable. */
data class HomeActions(
    val onModeChange: (TravelMode) -> Unit = {},
    val onOpenSheet: (HomeSheet) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onRecentClick: (RecentSearch) -> Unit = {},
    val onPopularClick: (PopularRoute) -> Unit = {},
    val onNotificationsClick: () -> Unit = {},
    val onSeeAllOffers: () -> Unit = {},
    val onAccountClick: () -> Unit = {},
    val flight: FlightSearchActions = FlightSearchActions(),
)

/** Stateless Home screen — used directly by previews and screenshot tests. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    now: LocalTime = remember { LocalTime.now() },
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = Spacing.lg),
    ) {
        item(key = "header") {
            HomeHeader(now = now, unread = state.unread, onNotificationsClick = actions.onNotificationsClick, onAccountClick = actions.onAccountClick)
        }
        item(key = "modes") {
            TravelModeTabs(
                selected = state.query.mode,
                onSelect = actions.onModeChange,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
            )
        }
        item(key = "search") {
            SearchCard(
                query = state.query,
                onFromClick = { actions.onOpenSheet(HomeSheet.FROM) },
                onToClick = { actions.onOpenSheet(HomeSheet.TO) },
                onSwap = actions.onSwap,
                onDateClick = { actions.onOpenSheet(HomeSheet.DATE) },
                onOptionsClick = { actions.onOpenSheet(HomeSheet.OPTIONS) },
                onSearch = actions.onSearch,
                errorText = state.error?.let { errorText(it) },
                modifier = Modifier.padding(horizontal = Spacing.screen),
                flightActions = actions.flight,
            )
        }

        if (state.recentSearches.isNotEmpty()) {
            item(key = "recent_title") {
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader(stringResource(R.string.home_recent_searches))
            }
            item(key = "recent") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.recentSearches, key = { it.id }) { RecentSearchCard(it) { actions.onRecentClick(it) } }
                }
            }
        }

        if (state.popularRoutes.isNotEmpty()) {
            item(key = "popular_title") {
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader(stringResource(R.string.home_popular_routes))
            }
            item(key = "popular") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.popularRoutes, key = { it.from.id + it.to.id }) {
                        PopularRouteCard(it) { actions.onPopularClick(it) }
                    }
                }
            }
        }

        if (state.offers.isNotEmpty()) {
            item(key = "offers_title") {
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader(
                    title = stringResource(R.string.home_offers),
                    actionText = stringResource(R.string.home_see_all),
                    onAction = actions.onSeeAllOffers,
                )
            }
            item(key = "offers") { OfferCarousel(state.offers) }
        }
    }
}

/** Round bell + profile buttons, a small greeting and a big bold question, left-aligned. */
@Composable
private fun HomeHeader(now: LocalTime, unread: Int, onNotificationsClick: () -> Unit, onAccountClick: () -> Unit) {
    val greeting = stringResource(
        when (now.hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..16 -> R.string.home_greeting_afternoon
            in 17..20 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$greeting 👋",
                style = MaterialTheme.typography.bodyLarge,
                color = JatriTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Box {
                CircleIconButton(
                    icon = JatriIcons.NotificationsOutlined,
                    contentDescription = if (unread > 0) {
                        pluralStringResource(R.plurals.home_notifications_unread, unread, unread)
                    } else {
                        stringResource(R.string.home_notifications)
                    },
                    onClick = onNotificationsClick,
                )
                if (unread > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(10.dp)
                            .background(JatriPalette.SunsetOrange, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.xs))
            CircleIconButton(
                icon = JatriIcons.Person,
                contentDescription = stringResource(R.string.home_account),
                onClick = onAccountClick,
                selected = true,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun errorText(error: SearchError): String = when (error) {
    SearchError.MISSING_FROM -> stringResource(R.string.home_select_from)
    SearchError.MISSING_TO -> stringResource(R.string.home_select_to)
    SearchError.SAME_PLACE -> stringResource(com.jatri.core.ui.R.string.ui_same_city_error)
    SearchError.LEG_INCOMPLETE -> stringResource(R.string.home_leg_incomplete)
    SearchError.DATE_ORDER -> stringResource(R.string.home_date_order)
}

@Composable
private fun RecentSearchCard(search: RecentSearch, onClick: () -> Unit) {
    val lang = JatriTheme.language
    JatriCard(onClick = onClick, modifier = Modifier.width(200.dp), contentPadding = PaddingValues(Spacing.md), shape = JatriShapes.cardSmall) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(search.mode.icon, contentDescription = null, tint = search.mode.color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                "${search.from.city(lang)} → ${search.to.city(lang)}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            DateTimeFormats.shortDate(search.date, lang),
            style = MaterialTheme.typography.bodySmall,
            color = JatriTheme.colors.textSecondary,
        )
    }
}

/** Big card: gradient artwork on top, route and starting price below, black "View details" pill. */
@Composable
private fun PopularRouteCard(route: PopularRoute, onClick: () -> Unit) {
    val lang = JatriTheme.language
    JatriCard(onClick = onClick, modifier = Modifier.width(264.dp), contentPadding = PaddingValues(Spacing.xs)) {
        ModeIllustration(
            mode = route.mode,
            shape = JatriShapes.cardSmall,
            iconSize = 56.dp,
            modifier = Modifier.fillMaxWidth().height(132.dp),
        )
        Column(Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm)) {
            Text(
                "${route.from.city(lang)} → ${route.to.city(lang)}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_from_price, route.startingFrom.format(lang)),
                    style = MaterialTheme.typography.labelLarge,
                    color = JatriTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                JatriSmallButton(text = stringResource(R.string.home_details), onClick = onClick)
            }
        }
    }
}

@Composable
private fun OfferCarousel(offers: List<Offer>) {
    val pager = rememberPagerState { offers.size }
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        pageSpacing = Spacing.sm,
    ) { page -> OfferBanner(offers[page]) }
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(offers.size) { i ->
            val active = i == pager.currentPage
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else JatriTheme.colors.divider),
            )
        }
    }
}

private val offerGradients = listOf(
    listOf(JatriPalette.OceanTeal, Color(0xFF14A3AE)),
    listOf(Color(0xFFE5622F), JatriPalette.SunsetOrange),
    listOf(JatriPalette.RailMaroon, Color(0xFFD25563)),
)

@Composable
private fun OfferBanner(offer: Offer) {
    val lang = JatriTheme.language
    val index = (offer.id.hashCode() and Int.MAX_VALUE) % offerGradients.size
    // Ink text on the orange gradient keeps WCAG AA contrast; white on teal/maroon.
    val onBanner = if (index == 1) JatriPalette.Ink else Color.White
    Surface(shape = JatriShapes.card, modifier = Modifier.fillMaxWidth().height(140.dp)) {
        Box(Modifier.background(Brush.linearGradient(offerGradients[index])).padding(Spacing.md)) {
            Column(Modifier.align(Alignment.CenterStart).padding(end = 64.dp)) {
                Text(offer.title(lang), style = MaterialTheme.typography.titleLarge, color = onBanner, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(offer.subtitle(lang), style = MaterialTheme.typography.bodySmall, color = onBanner.copy(alpha = 0.9f))
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.home_offer_code, offer.code),
                    style = MaterialTheme.typography.labelLarge,
                    color = onBanner,
                    modifier = Modifier
                        .background(onBanner.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Icon(
                JatriIcons.Offer,
                contentDescription = null,
                tint = onBanner.copy(alpha = 0.25f),
                modifier = Modifier.align(Alignment.CenterEnd).size(72.dp).offset(x = 8.dp),
            )
        }
    }
}
