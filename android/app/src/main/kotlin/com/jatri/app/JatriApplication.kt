package com.jatri.app

import android.app.Application
import com.jatri.app.reminder.AlarmTripReminders
import com.jatri.core.data.reminder.TripReminders
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.BookingCache
import com.jatri.core.model.SessionState
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JatriApplication : Application() {
    @Inject lateinit var auth: AuthRepository

    @Inject lateinit var bookingCache: BookingCache

    @Inject lateinit var reminders: TripReminders

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AlarmTripReminders.createChannel(this)
        // After logout, nobody else's tickets stay on the phone and no reminders fire for them.
        scope.launch {
            auth.session.drop(1).filterIsInstance<SessionState.LoggedOut>().collect {
                bookingCache.clear()
                reminders.clear()
            }
        }
    }
}
