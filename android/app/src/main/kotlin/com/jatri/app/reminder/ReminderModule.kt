package com.jatri.app.reminder

import com.jatri.core.data.reminder.TripReminders
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds abstract fun bindsTripReminders(impl: AlarmTripReminders): TripReminders
}
