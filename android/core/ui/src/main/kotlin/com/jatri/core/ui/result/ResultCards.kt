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
import com.jatri.core.model.FlightOffer
import com.jatri.core.model.TrainClassFare
import com.jatri.core.model.TrainTrip
import com.jatri.core.model.TravelMode
import com.jatri.core.ui.R
import com.jatri.core.ui.color
import com.jatri.core.ui.label
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

private const val FEW_SEATS = 5

/** Flight result: airline, times with duration line, stops, baggage, refundable tag, price. */
@Composable
fun FlightResultCard(offer: FlightOffer, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lang = JatriTheme.language
    JatriCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OperatorAvatar(offer.airlineName, TravelMode.FLIGHT.color)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(offer.airlineName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(offer.flightNumber.localizeIf(lang), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
            }
            PriceBlock(offer.price.format(lang))
        }
        Spacer(Modifier.height(Spacing.md))
        TimeLine(
            departAt = offer.departAt,
            arriveAt = offer.arriveAt,
            fromCode = offer.from.code,
            toCode = offer.to.code,
            middle = if (offer.stops == 0) {
                stringResource(R.string.ui_nonstop)
            } else {
                pluralStringResource(R.plurals.ui_stops, offer.stops, offer.stops.localized(lang))
            },
            duration = offer.duration,
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                text = stringResource(R.string.ui_baggage_kg, offer.baggageKg.localized(lang)),
                tone = StatusTone.Neutral,
                icon = JatriIcons.Baggage,
            )
            if (offer.refundable) {
                StatusChip(stringResource(R.string.ui_refundable), StatusTone.Success)
            } else {
                StatusChip(stringResource(R.string.ui_non_refundable), StatusTone.Neutral)
            }
        }
    }
}

/** Bus result: operator, coach type, times, boarding point, seats left, price. */
@Composable
fun BusResultCard(trip: BusTrip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lang = JatriTheme.language
    JatriCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OperatorAvatar(trip.operatorName, TravelMode.BUS.color)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(trip.operatorName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    TagChip(trip.coachType.label(), TravelMode.BUS.color)
                    Text(trip.coachNumber, style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
                }
            }
            PriceBlock(trip.price.format(lang))
        }
        Spacer(Modifier.height(Spacing.md))
        TimeLine(
            departAt = trip.departAt,
            arriveAt = trip.arriveAt,
            fromCode = trip.from.city(lang),
            toCode = trip.to.city(lang),
            middle = null,
            duration = Duration.between(trip.departAt, trip.arriveAt),
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(JatriIcons.Location, contentDescription = null, tint = JatriTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.ui_boarding_at, trip.boardingPoint),
                style = MaterialTheme.typography.bodySmall,
                color = JatriTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SeatsChip(trip.seatsAvailable, lang)
        }
    }
}

/** Train result: name & number, times, off-day, a tappable fare box per class. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainResultCard(
    trip: TrainTrip,
    onClassClick: (TrainClassFare) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = JatriTheme.language
    JatriCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OperatorAvatar(trip.trainName, TravelMode.TRAIN.color)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    "${trip.trainName} (${trip.trainNumber.localizeIf(lang)})",
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
            }
        }
        Spacer(Modifier.height(Spacing.md))
        TimeLine(
            departAt = trip.departAt,
            arriveAt = trip.arriveAt,
            fromCode = trip.from.city(lang),
            toCode = trip.to.city(lang),
            middle = null,
            duration = Duration.between(trip.departAt, trip.arriveAt),
        )
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = JatriTheme.colors.divider)
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
        shape = JatriShapes.button,
        color = if (soldOut) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
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

/** "07:30 ——— 1h 05m ——— 08:35" with codes underneath. */
@Composable
private fun TimeLine(
    departAt: LocalDateTime,
    arriveAt: LocalDateTime,
    fromCode: String,
    toCode: String,
    middle: String?,
    duration: Duration,
) {
    val lang = JatriTheme.language
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(DateTimeFormats.time(departAt, lang), style = MaterialTheme.typography.titleLarge)
            Text(fromCode, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary, maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(DateTimeFormats.duration(duration, lang), style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot()
                HorizontalDivider(Modifier.weight(1f), color = JatriTheme.colors.divider, thickness = 1.5.dp)
                Dot()
            }
            if (middle != null) {
                Text(middle, style = MaterialTheme.typography.labelSmall, color = JatriTheme.colors.textSecondary)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(DateTimeFormats.time(arriveAt, lang), style = MaterialTheme.typography.titleLarge)
            Text(toCode, style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary, maxLines = 1, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Dot() {
    Box(Modifier.size(6.dp).background(JatriTheme.colors.divider, CircleShape))
}

/** Logo placeholder: first letter on a tinted circle. Real logos load with Coil in Phase 3/5. */
@Composable
private fun OperatorAvatar(name: String, tint: Color) {
    Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = tint)
    }
}

@Composable
private fun PriceBlock(price: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(price, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.ui_per_person), style = MaterialTheme.typography.labelSmall, color = JatriTheme.colors.textSecondary)
    }
}

private fun String.localizeIf(lang: AppLanguage) =
    localizeDigits(lang)

private fun AppLanguage.locale(): Locale = Locale.forLanguageTag(if (this == AppLanguage.BANGLA) "bn-BD" else "en-BD")
