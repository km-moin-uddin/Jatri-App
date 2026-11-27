package com.jatri.core.data.repository

import com.jatri.core.data.seed.MockContent
import com.jatri.core.data.seed.SeedLocations
import com.jatri.core.database.dao.RecentSearchDao
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.apiCall
import com.jatri.core.network.model.LocationDto
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locations from `GET /locations`, with the bundled seed list shown first and kept as the
 * offline fallback. Ids are shared (e.g. `air_DAC`) so saved searches always resolve.
 */
@Singleton
internal class NetworkLocationRepository @Inject constructor(
    private val api: JatriApi,
) : LocationRepository {
    private val fromServer = java.util.concurrent.ConcurrentHashMap<LocationType, List<Location>>()

    override fun observeLocations(type: LocationType): Flow<List<Location>> = flow {
        val seed = SeedLocations.all.filter { it.type == type }
        emit(fromServer[type] ?: seed)
        if (fromServer[type] == null) {
            val remote = runCatching { apiCall { api.locations(type = type.apiName()) } }.getOrNull()
                ?.mapNotNull { it.toModel() }
                .orEmpty()
            if (remote.isNotEmpty()) {
                fromServer[type] = remote
                emit(remote)
            }
        }
    }

    override suspend fun getById(id: String): Location? =
        fromServer.values.asSequence().flatten().firstOrNull { it.id == id } ?: SeedLocations.byId[id]

    private fun LocationType.apiName() = when (this) {
        LocationType.AIRPORT -> "airport"
        LocationType.BUS_CITY -> "bus_city"
        LocationType.STATION -> "station"
    }

    private fun LocationDto.toModel(): Location? {
        val t = when (type) {
            "airport" -> LocationType.AIRPORT
            "bus_city" -> LocationType.BUS_CITY
            "station" -> LocationType.STATION
            else -> return null
        }
        return Location(id, t, code, nameEn, nameBn, cityEn, cityBn, country, isPopular)
    }
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
    private val offerRepository: OfferRepository,
) : HomeContentRepository {
    override fun observePopularRoutes(mode: TravelMode): Flow<List<PopularRoute>> =
        flowOf(MockContent.popularRoutes.filter { it.mode == mode })

    /** Offers from the server (the bundled samples first, so Home never waits on the network). */
    override fun observeOffers(): Flow<List<Offer>> = kotlinx.coroutines.flow.flow {
        emit(MockContent.offers(LocalDate.now(clock)))
        emit(offerRepository.offers())
    }
}
