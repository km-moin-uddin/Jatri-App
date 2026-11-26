package com.jatri.core.ui.result

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.model.Location
import com.jatri.core.ui.R
import java.time.LocalDate

/** "Dhaka → Cox's Bazar" */
@Composable
fun routeTitle(from: Location?, to: Location?): String {
    val lang = JatriTheme.language
    return "${from?.city(lang).orEmpty()} → ${to?.city(lang).orEmpty()}"
}

/** "Thu, 27 Nov · 2 passengers" */
@Composable
fun routeSubtitle(date: LocalDate, passengers: Int): String {
    val lang = JatriTheme.language
    val pax = pluralStringResource(R.plurals.ui_passenger_count, passengers, passengers.localized(lang))
    return "${DateTimeFormats.shortDate(date, lang)} · $pax"
}
