package com.jatri.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jatri.core.data.repository.HomeContentRepository
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.repository.RecentSearchRepository
import com.jatri.core.model.Location
import com.jatri.core.model.Offer
import com.jatri.core.model.PopularRoute
import com.jatri.core.model.RecentSearch
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.TravelMode
import com.jatri.core.model.locationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class SearchError { MISSING_FROM, MISSING_TO, SAME_PLACE }

/** Which sheet is open on top of Home. */
enum class HomeSheet { NONE, FROM, TO, DATE, OPTIONS }

data class HomeUiState(
    val query: SearchQuery,
    val today: LocalDate,
    val locations: List<Location> = emptyList(),
    val recentSearches: List<RecentSearch> = emptyList(),
    val popularRoutes: List<PopularRoute> = emptyList(),
    val offers: List<Offer> = emptyList(),
    val sheet: HomeSheet = HomeSheet.NONE,
    val error: SearchError? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    locationRepository: LocationRepository,
    private val recentSearchRepository: RecentSearchRepository,
    homeContentRepository: HomeContentRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    /** One draft query per mode, so switching tabs keeps what the user already picked. */
    private val drafts = MutableStateFlow(
        TravelMode.entries.associateWith { SearchQuery(mode = it, from = null, to = null, date = today.plusDays(1)) },
    )
    private val mode = MutableStateFlow(TravelMode.BUS)
    private val sheet = MutableStateFlow(HomeSheet.NONE)
    private val error = MutableStateFlow<SearchError?>(null)

    private val modeContent = mode.flatMapLatest { m ->
        combine(
            locationRepository.observeLocations(m.locationType()),
            homeContentRepository.observePopularRoutes(m),
        ) { locations, popular -> locations to popular }
    }

    private val recent = mode.flatMapLatest { m ->
        recentSearchRepository.observeRecentSearches(limit = 10).map { list -> list.filter { it.mode == m }.take(4) }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(drafts, mode) { d, m -> d.getValue(m) },
        modeContent,
        recent,
        homeContentRepository.observeOffers(),
        combine(sheet, error) { s, e -> s to e },
    ) { query, content, recentList, offers, sheetAndError ->
        HomeUiState(
            query = query,
            today = today,
            locations = content.first,
            recentSearches = recentList,
            popularRoutes = content.second,
            offers = offers,
            sheet = sheetAndError.first,
            error = sheetAndError.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(query = drafts.value.getValue(TravelMode.BUS), today = today),
    )

    fun selectMode(newMode: TravelMode) {
        mode.value = newMode
        error.value = null
    }

    fun openSheet(target: HomeSheet) {
        sheet.value = target
    }

    fun closeSheet() {
        sheet.value = HomeSheet.NONE
    }

    fun setFrom(location: Location) {
        edit { it.copy(from = location) }
        closeSheet()
    }

    fun setTo(location: Location) {
        edit { it.copy(to = location) }
        closeSheet()
    }

    fun swap() = edit { it.copy(from = it.to, to = it.from) }

    fun setDate(date: LocalDate) = edit { it.copy(date = date) }

    fun updateQuery(query: SearchQuery) = edit { query }

    fun applyRecent(search: RecentSearch) {
        val date = if (search.date.isBefore(today)) today.plusDays(1) else search.date
        mode.value = search.mode
        edit { it.copy(from = search.from, to = search.to, date = date, passengers = search.passengers) }
    }

    fun applyPopular(route: PopularRoute) {
        mode.value = route.mode
        edit { it.copy(from = route.from, to = route.to) }
    }

    /** Validates, remembers the search, then hands it to [onValid] for navigation. */
    fun search(onValid: (SearchQuery) -> Unit) {
        val query = drafts.value.getValue(mode.value)
        val from = query.from
        val to = query.to
        val problem = when {
            from == null -> SearchError.MISSING_FROM
            to == null -> SearchError.MISSING_TO
            from.id == to.id -> SearchError.SAME_PLACE
            else -> null
        }
        error.value = problem
        if (problem != null) return
        viewModelScope.launch { recentSearchRepository.save(query) }
        onValid(query)
    }

    private fun edit(transform: (SearchQuery) -> SearchQuery) {
        drafts.update { all -> all + (mode.value to transform(all.getValue(mode.value))) }
        error.value = null
    }
}
