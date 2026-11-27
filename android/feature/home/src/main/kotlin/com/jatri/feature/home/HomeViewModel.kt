package com.jatri.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jatri.core.data.repository.HomeContentRepository
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.repository.NotificationRepository
import com.jatri.core.model.SessionState
import com.jatri.core.data.repository.RecentSearchRepository
import com.jatri.core.model.LegQuery
import com.jatri.core.model.Location
import com.jatri.core.model.Offer
import com.jatri.core.model.PopularRoute
import com.jatri.core.model.RecentSearch
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.TravelMode
import com.jatri.core.model.TripType
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

enum class SearchError { MISSING_FROM, MISSING_TO, SAME_PLACE, LEG_INCOMPLETE, DATE_ORDER }

/** Which sheet is open on top of Home. */
enum class HomeSheet { NONE, FROM, TO, DATE, RETURN_DATE, OPTIONS }

data class HomeUiState(
    val query: SearchQuery,
    val today: LocalDate,
    val locations: List<Location> = emptyList(),
    val recentSearches: List<RecentSearch> = emptyList(),
    val popularRoutes: List<PopularRoute> = emptyList(),
    val offers: List<Offer> = emptyList(),
    val sheet: HomeSheet = HomeSheet.NONE,
    /** -1 = the main from/to/date; 0.. = that extra multi-city flight. */
    val sheetLeg: Int = -1,
    val error: SearchError? = null,
    /** Unread notifications (bell badge). */
    val unread: Int = 0,
) {
    /** The date the open date sheet should show as selected. */
    val sheetDate: LocalDate
        get() = when {
            sheet == HomeSheet.RETURN_DATE -> query.returnDate ?: query.date
            sheetLeg >= 0 -> query.extraLegs.getOrNull(sheetLeg)?.date ?: query.date
            else -> query.date
        }

    /** Earliest date allowed in the open date sheet (a later flight can't leave before the previous one). */
    val sheetMinDate: LocalDate
        get() = when {
            sheet == HomeSheet.RETURN_DATE -> query.date
            sheetLeg > 0 -> query.extraLegs[sheetLeg - 1].date
            sheetLeg == 0 -> query.date
            else -> today
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    locationRepository: LocationRepository,
    private val recentSearchRepository: RecentSearchRepository,
    homeContentRepository: HomeContentRepository,
    private val notifications: NotificationRepository,
    private val auth: AuthRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    /** One draft query per mode, so switching tabs keeps what the user already picked. */
    private val drafts = MutableStateFlow(
        TravelMode.entries.associateWith { SearchQuery(mode = it, from = null, to = null, date = today.plusDays(1)) },
    )
    private val mode = MutableStateFlow(TravelMode.BUS)
    private val sheet = MutableStateFlow(HomeSheet.NONE)
    private val sheetLeg = MutableStateFlow(-1)
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
        combine(drafts, mode, notifications.unread) { d, m, u -> d.getValue(m) to u },
        modeContent,
        recent,
        homeContentRepository.observeOffers(),
        combine(sheet, sheetLeg, error) { s, l, e -> Triple(s, l, e) },
    ) { queryAndUnread, content, recentList, offers, sheetAndError ->
        HomeUiState(
            query = queryAndUnread.first,
            unread = queryAndUnread.second,
            today = today,
            locations = content.first,
            recentSearches = recentList,
            popularRoutes = content.second,
            offers = offers,
            sheet = sheetAndError.first,
            sheetLeg = sheetAndError.second,
            error = sheetAndError.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(query = drafts.value.getValue(TravelMode.BUS), today = today),
    )

    /** Refreshes the bell badge (on Home's resume). */
    fun refreshUnread() {
        if (auth.session.value !is SessionState.LoggedIn) return
        viewModelScope.launch { runCatching { notifications.inbox() } }
    }

    fun selectMode(newMode: TravelMode) {
        mode.value = newMode
        error.value = null
    }

    fun openSheet(target: HomeSheet) {
        sheetLeg.value = -1
        sheet.value = target
    }

    /** Opens the from/to/date sheet of extra multi-city flight [index]. */
    fun openLegSheet(target: HomeSheet, index: Int) {
        sheetLeg.value = index
        sheet.value = target
    }

    fun closeSheet() {
        sheet.value = HomeSheet.NONE
        sheetLeg.value = -1
    }

    fun setFrom(location: Location) {
        val leg = sheetLeg.value
        if (leg >= 0) editLeg(leg) { it.copy(from = location) } else edit { it.copy(from = location) }
        closeSheet()
    }

    fun setTo(location: Location) {
        val leg = sheetLeg.value
        if (leg >= 0) {
            editLeg(leg) { it.copy(to = location) }
        } else {
            edit { q ->
                // A new destination is the natural start of the next multi-city flight.
                val legs = q.extraLegs.mapIndexed { i, l -> if (i == 0 && l.from == null) l.copy(from = location) else l }
                q.copy(to = location, extraLegs = legs)
            }
        }
        closeSheet()
    }

    fun swap() = edit { it.copy(from = it.to, to = it.from) }

    /** Date sheet picked a day: the main date, the return date or a multi-city flight's date. */
    fun setDate(date: LocalDate) {
        val leg = sheetLeg.value
        when {
            sheet.value == HomeSheet.RETURN_DATE -> edit { it.copy(returnDate = date) }
            leg >= 0 -> editLeg(leg) { it.copy(date = date) }
            else -> edit { q -> q.copy(date = date, returnDate = q.returnDate?.let { maxOf(it, date) }) }
        }
        if (sheet.value == HomeSheet.RETURN_DATE || leg >= 0) closeSheet()
        keepLegsInOrder()
    }

    fun setTripType(type: TripType) = edit { q ->
        when (type) {
            TripType.ONE_WAY -> q.copy(tripType = type)
            TripType.ROUND_TRIP -> q.copy(tripType = type, returnDate = q.returnDate?.takeIf { !it.isBefore(q.date) } ?: q.date.plusDays(3))
            TripType.MULTI_CITY -> q.copy(
                tripType = type,
                extraLegs = q.extraLegs.ifEmpty { listOf(LegQuery(from = q.to, to = null, date = q.date.plusDays(2))) },
            )
        }
    }

    fun addLeg() = edit { q ->
        if (q.extraLegs.size + 1 >= SearchQuery.MAX_FLIGHT_LEGS) return@edit q
        val last = q.extraLegs.lastOrNull()
        q.copy(extraLegs = q.extraLegs + LegQuery(from = last?.to ?: q.to, to = null, date = (last?.date ?: q.date).plusDays(2)))
    }

    fun removeLeg(index: Int) = edit { q -> q.copy(extraLegs = q.extraLegs.filterIndexed { i, _ -> i != index }) }

    private fun editLeg(index: Int, transform: (LegQuery) -> LegQuery) =
        edit { q -> q.copy(extraLegs = q.extraLegs.mapIndexed { i, l -> if (i == index) transform(l) else l }) }

    /** Later flights never leave before earlier ones. */
    private fun keepLegsInOrder() = edit { q ->
        var previous = q.date
        q.copy(extraLegs = q.extraLegs.map { l -> l.copy(date = maxOf(l.date, previous)).also { previous = it.date } })
    }

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
        val multiCity = query.mode == TravelMode.FLIGHT && query.tripType == TripType.MULTI_CITY
        val problem = when {
            from == null -> SearchError.MISSING_FROM
            to == null -> SearchError.MISSING_TO
            from.id == to.id -> SearchError.SAME_PLACE
            multiCity && query.extraLegs.any { !it.isComplete } -> SearchError.LEG_INCOMPLETE
            multiCity && query.extraLegs.zipWithNext().any { (a, b) -> b.date.isBefore(a.date) } -> SearchError.DATE_ORDER
            query.mode == TravelMode.FLIGHT && query.tripType == TripType.ROUND_TRIP &&
                query.returnDate?.isBefore(query.date) == true -> SearchError.DATE_ORDER
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
