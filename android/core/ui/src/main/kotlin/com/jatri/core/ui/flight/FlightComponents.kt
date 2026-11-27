package com.jatri.core.ui.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.localizeKeepingCodes
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Booking
import com.jatri.core.model.BookingSegment
import com.jatri.core.model.CabinClass
import com.jatri.core.model.FareBrand
import com.jatri.core.model.FareRules
import com.jatri.core.model.FlightJourney
import com.jatri.core.model.FlightLeg
import com.jatri.core.model.Location
import com.jatri.core.model.PassengerType
import com.jatri.core.ui.R
import com.jatri.core.ui.label
import java.time.Duration

/** True when the second journey simply comes back (A→B, B→A): label them Outbound / Return. */
fun isReturnTrip(fromIds: List<String>, toIds: List<String>): Boolean =
    fromIds.size == 2 && fromIds[1] == toIds[0] && toIds[1] == fromIds[0]

/** "Outbound", "Return" or "Flight 2". */
@Composable
fun journeyTitle(index: Int, returnTrip: Boolean): String {
    val lang = JatriTheme.language
    return when {
        returnTrip && index == 0 -> stringResource(R.string.ui_outbound)
        returnTrip -> stringResource(R.string.ui_return)
        else -> stringResource(R.string.ui_flight_n, (index + 1).localized(lang))
    }
}

@Composable
fun FareBrand.label(): String = stringResource(
    when (this) {
        FareBrand.SAVER -> R.string.ui_fare_saver
        FareBrand.FLEX -> R.string.ui_fare_flex
        FareBrand.BUSINESS -> R.string.ui_fare_business
    },
)

@Composable
fun PassengerType.label(): String = stringResource(
    when (this) {
        PassengerType.ADULT -> R.string.ui_adult
        PassengerType.CHILD -> R.string.ui_child
        PassengerType.INFANT -> R.string.ui_infant
    },
)

/** A journey leg by leg: flight number, aircraft, times and airports, with layovers in between. */
@Composable
fun JourneyDetails(title: String, journey: FlightJourney, modifier: Modifier = Modifier) {
    val lang = JatriTheme.language
    JatriCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                DateTimeFormats.shortDate(journey.departAt.toLocalDate(), lang) + " · " + DateTimeFormats.duration(journey.duration, lang),
                style = MaterialTheme.typography.labelMedium,
                color = JatriTheme.colors.textSecondary,
            )
        }
        journey.legs.forEachIndexed { i, leg ->
            if (i > 0) Layover(journey.legs[i - 1], leg)
            Spacer(Modifier.height(Spacing.sm))
            LegBlock(leg)
        }
    }
}

@Composable
private fun LegBlock(leg: FlightLeg) {
    val lang = JatriTheme.language
    Text(
        "${leg.carrierName} · ${leg.flightNumber.localizeKeepingCodes(lang)} · ${leg.aircraft}",
        style = MaterialTheme.typography.labelMedium,
        color = JatriTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(Spacing.xs))
    Stop(time = DateTimeFormats.time(leg.departAt, lang), place = leg.from, filled = true)
    Row(Modifier.padding(start = 3.dp)) {
        Box(Modifier.width(2.dp).height(20.dp).background(JatriTheme.colors.divider))
        Text(
            DateTimeFormats.duration(leg.duration, lang),
            style = MaterialTheme.typography.labelSmall,
            color = JatriTheme.colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
    Stop(time = DateTimeFormats.time(leg.arriveAt, lang), place = leg.to, filled = false)
}

@Composable
private fun Stop(time: String, place: Location, filled: Boolean) {
    val lang = JatriTheme.language
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (filled) MaterialTheme.colorScheme.onSurface else JatriTheme.colors.divider, CircleShape),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(time, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
        Text(
            "${place.code} · ${place.name(lang)}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun Layover(previous: FlightLeg, next: FlightLeg) {
    val lang = JatriTheme.language
    // Both times are local to the connecting airport, so the difference is the real wait.
    val wait = Duration.between(previous.arriveAt, next.departAt)
    Spacer(Modifier.height(Spacing.sm))
    Text(
        stringResource(R.string.ui_layover, DateTimeFormats.duration(wait, lang), previous.to.city(lang), previous.to.code),
        style = MaterialTheme.typography.labelMedium,
        color = JatriTheme.colors.warning,
        modifier = Modifier
            .fillMaxWidth()
            .background(JatriTheme.colors.warningContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/** Baggage, refund, date change, deadline and no-show rules of a fare, in plain words. */
@Composable
fun FareRulesCard(rules: FareRules, brand: FareBrand?, cabin: CabinClass?, modifier: Modifier = Modifier) {
    val lang = JatriTheme.language
    JatriCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui_fare_rules), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                listOfNotNull(cabin?.label(), brand?.label()).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = JatriTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RuleRow(
                JatriIcons.Baggage,
                stringResource(R.string.ui_rule_baggage, rules.checkedBaggageKg.localized(lang), rules.cabinBaggageKg.localized(lang)),
            )
            RuleRow(
                JatriIcons.Document,
                rules.refundFee?.let { stringResource(R.string.ui_rule_refund_fee, it.format(lang)) }
                    ?: stringResource(R.string.ui_rule_no_refund),
                good = rules.refundable,
            )
            RuleRow(
                JatriIcons.Calendar,
                when (val fee = rules.changeFee) {
                    null -> stringResource(R.string.ui_rule_no_change)
                    else -> if (fee.paisa == 0L) stringResource(R.string.ui_rule_free_change) else stringResource(R.string.ui_rule_change_fee, fee.format(lang))
                },
            )
            RuleRow(JatriIcons.Time, stringResource(R.string.ui_rule_deadline, rules.deadlineHours.localized(lang)))
            RuleRow(
                JatriIcons.Info,
                stringResource(if (rules.refundable) R.string.ui_rule_no_show_refund else R.string.ui_rule_no_show_forfeit),
            )
        }
    }
}

@Composable
private fun RuleRow(icon: ImageVector, text: String, good: Boolean? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = when (good) {
                true -> JatriTheme.colors.success
                false -> JatriTheme.colors.danger
                null -> JatriTheme.colors.textSecondary
            },
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A booked trip's flights, journey by journey: "S1 488 · DAC 07:00 → CXB 08:05". */
@Composable
fun BookedFlights(booking: Booking, places: (String) -> Location?) {
    val lang = JatriTheme.language
    val journeys = booking.journeys
    val returnTrip = isReturnTrip(journeys.map { it.first().fromId }, journeys.map { it.last().toId })
    journeys.forEachIndexed { j, legs ->
        if (j > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = JatriTheme.colors.divider)
        Text(
            journeyTitle(j, returnTrip) + " · " + DateTimeFormats.shortDate(legs.first().departAt.toLocalDate(), lang),
            style = MaterialTheme.typography.titleSmall,
        )
        legs.forEach { seg -> BookedLeg(seg, places) }
    }
}

@Composable
private fun BookedLeg(seg: BookingSegment, places: (String) -> Location?) {
    val lang = JatriTheme.language
    fun code(id: String) = places(id)?.code ?: id.removePrefix("air_")
    Text(
        "${seg.number.localizeKeepingCodes(lang)} · ${code(seg.fromId)} ${DateTimeFormats.time(seg.departAt, lang)} → " +
            "${code(seg.toId)} ${DateTimeFormats.time(seg.arriveAt, lang)}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        listOfNotNull(seg.carrier, seg.aircraft, seg.baggage?.let { stringResource(R.string.ui_baggage_short, it.localizeDigits(lang)) })
            .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = JatriTheme.colors.textSecondary,
    )
}
