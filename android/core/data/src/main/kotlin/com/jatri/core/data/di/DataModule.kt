package com.jatri.core.data.di

import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.BookingRepository
import com.jatri.core.data.repository.BusRepository
import com.jatri.core.data.repository.FlightRepository
import com.jatri.core.data.repository.NetworkFlightRepository
import com.jatri.core.data.repository.NetworkTrainRepository
import com.jatri.core.data.repository.NetworkNotificationRepository
import com.jatri.core.data.repository.NotificationRepository
import com.jatri.core.data.repository.TrainRepository
import com.jatri.core.data.repository.NetworkBookingRepository
import com.jatri.core.data.repository.NetworkBusRepository
import com.jatri.core.data.repository.NetworkPaymentRepository
import com.jatri.core.data.repository.PaymentRepository
import com.jatri.core.data.repository.DataStoreUserSettingsRepository
import com.jatri.core.data.repository.DefaultAuthRepository
import com.jatri.core.data.repository.HomeContentRepository
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.data.repository.MockHomeContentRepository
import com.jatri.core.data.repository.RecentSearchRepository
import com.jatri.core.data.repository.RoomRecentSearchRepository
import com.jatri.core.data.repository.NetworkLocationRepository
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
    @Binds abstract fun bindsLocationRepository(impl: NetworkLocationRepository): LocationRepository
    @Binds abstract fun bindsRecentSearchRepository(impl: RoomRecentSearchRepository): RecentSearchRepository
    @Binds abstract fun bindsUserSettingsRepository(impl: DataStoreUserSettingsRepository): UserSettingsRepository
    @Binds abstract fun bindsHomeContentRepository(impl: MockHomeContentRepository): HomeContentRepository
    @Binds abstract fun bindsAuthRepository(impl: DefaultAuthRepository): AuthRepository
    @Binds abstract fun bindsBusRepository(impl: NetworkBusRepository): BusRepository
    @Binds abstract fun bindsFlightRepository(impl: NetworkFlightRepository): FlightRepository
    @Binds abstract fun bindsTrainRepository(impl: NetworkTrainRepository): TrainRepository
    @Binds abstract fun bindsNotificationRepository(impl: NetworkNotificationRepository): NotificationRepository
    @Binds abstract fun bindsBookingRepository(impl: NetworkBookingRepository): BookingRepository
    @Binds abstract fun bindsPaymentRepository(impl: NetworkPaymentRepository): PaymentRepository

    companion object {
        @Provides fun providesClock(): Clock = Clock.systemDefaultZone()
    }
}
