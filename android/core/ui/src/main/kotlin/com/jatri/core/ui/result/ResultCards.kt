package com.jatri.core.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localized
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.component.TagChip
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriShapes
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.BusTrip
import com.jatri.core.model.FlightJourney
import com.jatri.core.model.FlightOffer
import com.jatri.core.model.TrainClassFare
import com.jatri.core.model.TrainTrip
import com.jatri.core.model.TravelMode
import com.jatri.core.ui.R
import com.jatri.core.ui.color
import com.jatri.core.ui.icon
import com.jatri.core.ui.label
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

private const val FEW_SEATS = 5

/**
 * Flight result: one compact time line per journey (outbound, return, multi-city flights), the
 * airline(s) and total price, then baggage / refund / seats-left chips.
 */
@Composable
fun FlightResultCard(offer: FlightOffer, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lang = JatriTheme.language
    JatriCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        offer.journeys.forEachIndexed { i, journey ->
            if (i > 0) Spacer(Modifier.height(Spacing.sm))
            JourneyLine(journey)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    offer.airlines.joinToString(" + "),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    offer.journeys.joinToString(", ") { j -> j.legs.joinToString(" / ") { it.flightNumber } }.localizeDigits(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = JatriTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PriceBlock(offer.price.format(lang))
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                text = stringResource(R.string.ui_baggage_kg, offer.rules.checkedBaggageKg.localized(lang)),
                tone = StatusTone.Neutral,
                icon = JatriIcons.Baggage,
            )
            if (offer.rules.refundable) {
                StatusChip(stringResource(R.string.ui_refundable), StatusTone.Success)
            } else {
                StatusChip(stringResource(R.string.ui_non_refundable), StatusTone.Neutral)
            }
            if (offer.seatsLeft <= FEW_FLIGHT_SEATS) {
                StatusChip(stringResource(R.string.ui_seats_left, offer.seatsLeft.localized(lang)), StatusTone.Warning)
            }
        }
    }
}

private const val FEW_FLIGHT_SEATS = 4

/** "07:00 DAC —— 1h 05m · Nonstop —— 08:05 CXB" with "+1" when it lands on a later day. */
@Composable
fun JourneyLine(journey: FlightJourney, modifier: Modifier = Modifier) {
    val lang = JatriTheme.language
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(DateTimeFormats.time(journey.departAt, lang), style = MaterialTheme.typography.titleLarge)
            Text(journey.from.code, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                DateTimeFormats.duration(journey.duration, lang),
                style = MaterialTheme.typography.labelSmall,
                color = JatriTheme.colors.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(filled = true)
                HorizontalDivider(Modifier.weight(1f), color = JatriTheme.colors.divider, thickness = 1.5.dp)
                Icon(TravelMode.FLIGHT.icon, contentDescription = null, modifier = Modifier.size(16.dp).padding(horizontal = 2.dp))
                HorizontalDivider(Modifier.weight(1f), color = JatriTheme.colors.divider, thickness = 1.5.dp)
                Dot(filled = false)
            }
            Text(
                if (journey.stops == 0) {
                    stringResource(R.string.ui_nonstop)
                } else {
                    pluralStringResource(R.plurals.ui_stops, journey.stops, journey.stops.localized(lang)) +
                        " · " + journey.legs.dropLast(1).joinToString { it.to.code }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (journey.stops == 0) JatriTheme.colors.success else JatriTheme.colors.textSecondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Top) {
                Text(DateTimeFormats.time(journey.arriveAt, lang), style = MaterialTheme.typography.titleLarge)
                if (journey.dayShift > 0) {
                    Text(
                        "+${journey.dayShift}".localizeDigits(lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = JatriTheme.colors.danger,
                    )
                }
            }
            Text(journey.to.code, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
        }
    }
}

/** Bus result: times + route line, operator & coach type, boarding point, seats left, price. */
@Composable
fun BusResultCard(trip: BusTrip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lang = JatriTheme.language
    JatriCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        TimeLine(
            mode = TravelMode.BUS,
            departAt = trip.departAt,
            arriveAt = trip.arriveAt,
            fromLabel = trip.from.city(lang),
            toLabel = trip.to.city(lang),
            caption = DateTimeFormats.duration(Duration.between(trip.departAt, trip.arriveAt), lang),
        )
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        trip.operatorName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    TagChip(trip.coachType.label(), TravelMode.BUS.color)
                }
                Text(
                    stringResource(R.string.ui_boarding_at, trip.boardingPoint),
                    style = MaterialTheme.typography.bodySmall,
                    color = JatriTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PriceBlock(trip.price.format(lang))
        }
        Spacer(Modifier.height(Spacing.sm))
        SeatsChip(trip.seatsAvailable, lang)
    }
}

/** Train result: times + route line, name & number, off-day, a tappable fare pill per class. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainResultCard(
    trip: TrainTrip,
    onClassClick: (TrainClassFare) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = JatriTheme.language
    JatriCard(modifier = modifier.fillMaxWidth()) {
        TimeLine(
            mode = TravelMode.TRAIN,
            departAt = trip.departAt,
            arriveAt = trip.arriveAt,
            fromLabel = trip.from.city(lang),
            toLabel = trip.to.city(lang),
            caption = DateTimeFormats.duration(Duration.between(trip.departAt, trip.arriveAt), lang),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            "${trip.trainName} (${trip.trainNumber.localizeDigits(lang)})",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trip.offDay?.let { day ->
            Text(
                stringResource(R.string.ui_off_day, day.getDisplayName(TextStyle.FULL, lang.locale())),
                style = MaterialTheme.typography.bodySmall,
                color = JatriTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            trip.classes.forEach { fare -> TrainFareBox(fare, lang) { onClassClick(fare) } }
        }
    }
}

@Composable
private fun TrainFareBox(fare: TrainClassFare, lang: AppLanguage, onClick: () -> Unit) {
    val soldOut = fare.seatsAvailable == 0
    Surface(
        onClick = onClick,
        enabled = !soldOut,
        shape = JatriShapes.cardSmall,
        color = if (soldOut) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            Text(fare.trainClass.label(), style = MaterialTheme.typography.labelLarge)
            Text(fare.price.format(lang), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (soldOut) {
                    stringResource(R.string.ui_sold_out)
                } else {
                    stringResource(R.string.ui_seats_available, fare.seatsAvailable.localized(lang))
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    soldOut -> JatriTheme.colors.danger
                    fare.seatsAvailable <= FEW_SEATS -> JatriTheme.colors.warning
                    else -> JatriTheme.colors.success
                },
            )
        }
    }
}

@Composable
private fun SeatsChip(seats: Int, lang: AppLanguage) {
    when {
        seats == 0 -> StatusChip(stringResource(R.string.ui_sold_out), StatusTone.Danger)
        seats <= FEW_SEATS -> StatusChip(stringResource(R.string.ui_seats_left, seats.localized(lang)), StatusTone.Warning)
        else -> StatusChip(stringResource(R.string.ui_seats_available, seats.localized(lang)), StatusTone.Success)
    }
}

/**
 * 07:30                         08:35
 * DAC                             CXB
 *  •────────── (✈) ──────────•
 *            1h 05m · Non-stop
 */
@Composable
private fun TimeLine(
    mode: TravelMode,
    departAt: LocalDateTime,
    arriveAt: LocalDateTime,
    fromLabel: String,
    toLabel: String,
    caption: String,
) {
    val lang = JatriTheme.language
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(DateTimeFormats.time(departAt, lang), style = MaterialTheme.typography.headlineSmall)
            Text(fromLabel, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(DateTimeFormats.time(arriveAt, lang), style = MaterialTheme.typography.headlineSmall)
            Text(toLabel, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
    }
    Spacer(Modifier.height(Spacing.xs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(filled = true)
        HorizontalDivider(Modifier.weight(1f), color = JatriTheme.colors.divider, thickness = 1.5.dp)
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(mode.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
        HorizontalDivider(Modifier.weight(1f), color = JatriTheme.colors.divider, thickness = 1.5.dp)
        Dot(filled = false)
    }
    Text(
        caption,
        style = MaterialTheme.typography.labelSmall,
        color = JatriTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Dot(filled: Boolean) {
    Box(
        Modifier.size(8.dp).background(
            if (filled) MaterialTheme.colorScheme.onSurface else JatriTheme.colors.divider,
            CircleShape,
        ),
    )
}

@Composable
private fun PriceBlock(price: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(price, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_per_person), style = MaterialTheme.typography.labelSmall, color = JatriTheme.colors.textSecondary)
    }
}

private fun AppLanguage.locale(): Locale = Locale.forLanguageTag(if (this == AppLanguage.BANGLA) "bn-BD" else "en-BD")
