package com.jatri.core.data.di

import com.jatri.core.data.repository.DataStoreUserSettingsRepository
import com.jatri.core.data.repository.HomeContentRepository
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.repository.MockHomeContentRepository
import com.jatri.core.data.repository.RecentSearchRepository
import com.jatri.core.data.repository.RoomRecentSearchRepository
import com.jatri.core.data.repository.SeedLocationRepository
import com.jatri.core.data.repository.UserSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds abstract fun bindsLocationRepository(impl: SeedLocationRepository): LocationRepository
    @Binds abstract fun bindsRecentSearchRepository(impl: RoomRecentSearchRepository): RecentSearchRepository
    @Binds abstract fun bindsUserSettingsRepository(impl: DataStoreUserSettingsRepository): UserSettingsRepository
    @Binds abstract fun bindsHomeContentRepository(impl: MockHomeContentRepository): HomeContentRepository

    companion object {
        @Provides fun providesClock(): Clock = Clock.systemDefaultZone()
    }
}
