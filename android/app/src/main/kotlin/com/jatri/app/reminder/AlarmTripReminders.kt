package com.jatri.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jatri.app.MainActivity
import com.jatri.app.R
import com.jatri.core.data.reminder.TripReminders
import com.jatri.core.model.Booking
import com.jatri.core.model.BookingStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Journey reminders 24 h and 3 h before departure, scheduled on the phone with AlarmManager
 * (inexact, battery-friendly; no exact-alarm permission). They arrive without push or internet.
 */
@Singleton
class AlarmTripReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : TripReminders {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("trip_reminders", Context.MODE_PRIVATE)

    override fun sync(bookings: List<Booking>) {
        val now = clock.millis()
        val wanted = mutableMapOf<String, Reminder>()
        bookings.filter { it.status == BookingStatus.TICKETED }.forEach { b ->
            val depart = b.departAt ?: return@forEach
            // Bus and train times are Bangladesh time; flights carry the airport's wall clock, and
            // the first flight leaves from Bangladesh in practice. Good enough for a reminder.
            val at = depart.atZone(DHAKA).toInstant().toEpochMilli()
            HOURS.forEach { h ->
                val fire = at - h * 3_600_000L
                if (fire > now) wanted["${b.id}|$h"] = Reminder(b.id, h, fire, b.ref, depart.format(TIME))
            }
        }
        val scheduled = prefs.getStringSet(KEY, emptySet()).orEmpty()
        (scheduled - wanted.keys).forEach { key -> alarms.cancel(pending(key, null)) }
        wanted.forEach { (key, r) ->
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt, pending(key, r))
        }
        prefs.edit { putStringSet(KEY, wanted.keys) }
    }

    override fun clear() {
        prefs.getStringSet(KEY, emptySet()).orEmpty().forEach { alarms.cancel(pending(it, null)) }
        prefs.edit { remove(KEY) }
    }

    private fun pending(key: String, r: Reminder?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION).setData("jatri://reminder/$key".toUri())
        if (r != null) {
            intent.putExtra(EXTRA_BOOKING, r.bookingId).putExtra(EXTRA_HOURS, r.hours).putExtra(EXTRA_REF, r.ref).putExtra(EXTRA_TIME, r.time)
        }
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private data class Reminder(val bookingId: String, val hours: Int, val fireAt: Long, val ref: String, val time: String)

    companion object {
        val HOURS = listOf(24, 3)
        private val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")
        private val TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm")
        private const val KEY = "scheduled"
        const val ACTION = "com.jatri.app.TRIP_REMINDER"
        const val CHANNEL = "trips"
        const val EXTRA_BOOKING = "booking_id"
        const val EXTRA_HOURS = "hours"
        const val EXTRA_REF = "ref"
        const val EXTRA_TIME = "time"

        fun createChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.reminder_channel))
                .setDescription(context.getString(R.string.reminder_channel_desc))
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}

/** Shows the reminder; tapping it opens the ticket. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getStringExtra(AlarmTripReminders.EXTRA_BOOKING) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val hours = intent.getIntExtra(AlarmTripReminders.EXTRA_HOURS, 24)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_BOOKING, bookingId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, bookingId.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, AlarmTripReminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jatri)
            .setContentTitle(context.resources.getQuantityString(R.plurals.reminder_title, hours, hours))
            .setContentText(
                context.getString(
                    R.string.reminder_body,
                    intent.getStringExtra(AlarmTripReminders.EXTRA_TIME).orEmpty(),
                    intent.getStringExtra(AlarmTripReminders.EXTRA_REF).orEmpty(),
                ),
            )
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify("$bookingId|$hours".hashCode(), notification)
    }
}
