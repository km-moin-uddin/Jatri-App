package com.jatri.core.data.reminder

import com.jatri.core.model.Booking

/**
 * Journey reminders on the phone itself (24 h and 3 h before departure), so they arrive even
 * without push or internet. The app module implements it with AlarmManager.
 */
interface TripReminders {
    /** Schedules reminders for upcoming tickets and drops those of cancelled or past trips. */
    fun sync(bookings: List<Booking>)

    fun clear()
}
