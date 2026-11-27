package com.jatri.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Opens (or shares) a downloaded PDF ticket through the app's FileProvider. */
fun openTicketPdf(context: Context, file: File, share: Boolean) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = if (share) {
        Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
    } else {
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(if (share) Intent.createChooser(intent, null) else intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.ui_no_pdf_app, Toast.LENGTH_LONG).show()
    }
}

/**
 * Hands the trip to the phone's calendar app (the user confirms there; no calendar permission).
 * Times are local wall-clock times at the place of departure; Bangladesh time is used for buses
 * and trains, the airport's zone is not known here so flights use the phone's zone.
 */
fun addTripToCalendar(context: Context, title: String, place: String, start: LocalDateTime, end: LocalDateTime, notes: String) {
    val zone = ZoneId.of("Asia/Dhaka")
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, place)
        .putExtra(CalendarContract.Events.DESCRIPTION, notes)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atZone(zone).toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.atZone(zone).toInstant().toEpochMilli())
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.ui_no_calendar_app, Toast.LENGTH_LONG).show()
    }
}
