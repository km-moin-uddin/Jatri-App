package com.jatri.core.data.repository

import com.jatri.core.data.seed.MockContent
import com.jatri.core.data.seed.SeedLocations
import com.jatri.core.database.dao.RecentSearchDao
import com.jatri.core.database.model.RecentSearchEntity
import com.jatri.core.datastore.UserPreferencesDataSource
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.Offer
import com.jatri.core.model.PassengerCount
import com.jatri.core.model.PopularRoute
import com.jatri.core.model.RecentSearch
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.ThemeMode
import com.jatri.core.model.TravelMode
import com.jatri.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Seed-list implementation. Replaced by `GET /locations` (with this list as fallback) in Phase 2. */
@Singleton
internal class SeedLocationRepository @Inject constructor() : LocationRepository {
    override fun observeLocations(type: LocationType): Flow<List<Location>> =
        flowOf(SeedLocations.all.filter { it.type == type })

    override suspend fun getById(id: String): Location? = SeedLocations.byId[id]
}

@Singleton
internal class RoomRecentSearchRepository @Inject constructor(
    private val dao: RecentSearchDao,
    private val clock: Clock,
) : RecentSearchRepository {
    override fun observeRecentSearches(limit: Int): Flow<List<RecentSearch>> =
        dao.observeRecent(limit).map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun save(query: SearchQuery) {
        val from = query.from ?: return
        val to = query.to ?: return
        val date = query.date.toString()
        val mode = query.mode.name
        val existing = dao.findId(mode, from.id, to.id, date)
        dao.upsert(
            RecentSearchEntity(
                id = existing ?: 0,
                mode = mode,
                fromId = from.id,
                toId = to.id,
                date = date,
                adults = query.passengers.adults,
                children = query.passengers.children,
                infants = query.passengers.infants,
                searchedAt = clock.millis(),
            ),
        )
        dao.trim(keep = 20)
    }

    private fun RecentSearchEntity.toModel(): RecentSearch? {
        val from = SeedLocations.byId[fromId] ?: return null
        val to = SeedLocations.byId[toId] ?: return null
        val mode = runCatching { TravelMode.valueOf(mode) }.getOrNull() ?: return null
        return RecentSearch(id, mode, from, to, LocalDate.parse(date), PassengerCount(adults, children, infants))
    }
}

@Singleton
internal class DataStoreUserSettingsRepository @Inject constructor(
    private val source: UserPreferencesDataSource,
) : UserSettingsRepository {
    override val settings: Flow<UserSettings> = source.settings
    override suspend fun setLanguage(language: AppLanguage) = source.setLanguage(language)
    override suspend fun setThemeMode(mode: ThemeMode) = source.setThemeMode(mode)
    override suspend fun setOnboardingCompleted(done: Boolean) = source.setOnboardingCompleted(done)
    override suspend fun setNotificationsEnabled(enabled: Boolean) = source.setNotificationsEnabled(enabled)
}

/** MOCK: popular routes and offers. Comes from the backend in Phase 8. */
@Singleton
internal class MockHomeContentRepository @Inject constructor(
    private val clock: Clock,
) : HomeContentRepository {
    override fun observePopularRoutes(mode: TravelMode): Flow<List<PopularRoute>> =
        flowOf(MockContent.popularRoutes.filter { it.mode == mode })

    override fun observeOffers(): Flow<List<Offer>> = flowOf(MockContent.offers(LocalDate.now(clock)))
}
