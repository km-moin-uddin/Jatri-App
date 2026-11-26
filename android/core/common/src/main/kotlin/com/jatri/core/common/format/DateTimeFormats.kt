package com.jatri.core.common.format

import com.jatri.core.model.AppLanguage
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date and time strings for the UI, with Bangla month/day names and digits when needed. */
object DateTimeFormats {
    private val bangla: Locale = Locale.forLanguageTag("bn-BD")
    private val english: Locale = Locale.forLanguageTag("en-BD")

    private fun locale(language: AppLanguage) = if (language == AppLanguage.BANGLA) bangla else english

    private fun pattern(pattern: String, language: AppLanguage) =
        DateTimeFormatter.ofPattern(pattern, locale(language))

    /** "Thu, 27 Nov" */
    fun shortDate(date: LocalDate, language: AppLanguage): String =
        date.format(pattern("EEE, d MMM", language)).localizeDigits(language)

    /** "27 November 2026" */
    fun longDate(date: LocalDate, language: AppLanguage): String =
        date.format(pattern("d MMMM yyyy", language)).localizeDigits(language)

    /** "Thu" */
    fun dayOfWeek(date: LocalDate, language: AppLanguage): String =
        date.format(pattern("EEE", language))

    /** "27" */
    fun dayOfMonth(date: LocalDate, language: AppLanguage): String =
        date.dayOfMonth.toString().localizeDigits(language)

    /** 24-hour "07:30" — travel tickets in Bangladesh use 24h or AM/PM; 24h is unambiguous. */
    fun time(time: LocalTime, language: AppLanguage): String =
        time.format(DateTimeFormatter.ofPattern("HH:mm")).localizeDigits(language)

    fun time(dateTime: LocalDateTime, language: AppLanguage): String = time(dateTime.toLocalTime(), language)

    /** "1h 05m" or "১ ঘ ০৫ মি" */
    fun duration(duration: Duration, language: AppLanguage): String {
        val hours = duration.toHours()
        val minutes = (duration.toMinutes() % 60).toString().padStart(2, '0')
        val text = if (language == AppLanguage.BANGLA) "$hours ঘ $minutes মি" else "${hours}h ${minutes}m"
        return text.localizeDigits(language)
    }
}
