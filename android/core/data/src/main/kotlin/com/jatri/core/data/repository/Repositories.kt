package com.jatri.core.data.repository

import com.jatri.core.model.AppLanguage
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.Offer
import com.jatri.core.model.PopularRoute
import com.jatri.core.model.RecentSearch
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.ThemeMode
import com.jatri.core.model.TravelMode
import com.jatri.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeLocations(type: LocationType): Flow<List<Location>>
    suspend fun getById(id: String): Location?
}

interface RecentSearchRepository {
    fun observeRecentSearches(limit: Int = 5): Flow<List<RecentSearch>>
    suspend fun save(query: SearchQuery)
}

interface UserSettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOnboardingCompleted(done: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
}

interface HomeContentRepository {
    fun observePopularRoutes(mode: TravelMode): Flow<List<PopularRoute>>
    fun observeOffers(): Flow<List<Offer>>
}
