package com.jatri.app

import android.app.Application
import android.os.StrictMode
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
        if (BuildConfig.DEBUG) {
            // Debug builds log disk or network work on the main thread and leaked resources, so
            // jank is caught while developing. Logged only: the demo APK must never crash on it.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
        }
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
