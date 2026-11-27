package com.jatri.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.localizeKeepingCodes
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.JatriBottomSheet
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.JatriTextButton
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.LoadingState
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.TicketCard
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Booking
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Money
import com.jatri.core.model.RefundStatus
import com.jatri.core.model.TravelMode
import com.jatri.core.ui.QrCode
import com.jatri.core.ui.addTripToCalendar
import com.jatri.core.ui.flight.BookedFlights
import com.jatri.core.ui.flight.label
import com.jatri.core.ui.label
import com.jatri.core.ui.openTicketPdf
import com.jatri.core.ui.text
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun TicketDetailRoute(onBack: () -> Unit, viewModel: TicketDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val cancelledText = stringResource(R.string.trips_cancelled_done)
    LaunchedEffect(state.cancelledNow) {
        if (state.cancelledNow) {
            snackbar.showSnackbar(cancelledText)
            viewModel.messageShown()
        }
    }
    val title = state.booking?.let { tripTitle(it, state.places) }.orEmpty()
    val calendarNotes = stringResource(R.string.trips_calendar_notes, state.booking?.ref.orEmpty())
    TicketDetailScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onRetry = viewModel::retry,
        onPdf = { share -> viewModel.pdf { openTicketPdf(context, it, share) } },
        onCalendar = {
            val b = state.booking ?: return@TicketDetailScreen
            val first = b.segments.first()
            val last = b.journeys.first().last()
            addTripToCalendar(
                context,
                title = "$title · ${first.carrier} ${first.number}",
                place = state.places[first.fromId]?.nameEn ?: first.fromId,
                start = first.departAt,
                end = last.arriveAt,
                notes = calendarNotes,
            )
        },
        onCancel = viewModel::startCancel,
        onDismissCancel = viewModel::dismissCancel,
        onConfirmCancel = viewModel::confirmCancel,
    )
}

/** The ticket (QR works offline), its timeline, PDF/share/calendar and cancellation. Stateless. */
@Composable
fun TicketDetailScreen(
    state: TicketDetailUiState,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPdf: (share: Boolean) -> Unit = {},
    onCalendar: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDismissCancel: () -> Unit = {},
    onConfirmCancel: () -> Unit = {},
) {
    val lang = JatriTheme.language
    val b = state.booking
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = stringResource(R.string.trips_detail_title), subtitle = b?.ref, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            b == null -> ErrorState(onRetry = onRetry, message = state.error?.text(), modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val (label, tone) = statusChip(b)
                StatusChip(label, tone)
                TicketCard(
                    modifier = Modifier.fillMaxWidth(),
                    notchOffsetFraction = 0.55f,
                    top = { TicketTop(b, state) },
                    bottom = {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.trips_pnr), style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
                                    Text(b.ref, style = MaterialTheme.typography.headlineSmall)
                                }
                                Text(b.total.format(lang), style = MaterialTheme.typography.titleLarge)
                            }
                            val ticket = b.ticket
                            if (ticket != null && b.status == BookingStatus.TICKETED) {
                                Spacer(Modifier.height(Spacing.sm))
                                QrCode(payload = ticket.qrPayload, contentDescription = stringResource(R.string.trips_qr))
                                Text(
                                    stringResource(R.string.trips_qr_offline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JatriTheme.colors.textSecondary,
                                )
                            }
                        }
                    },
                )
                if (b.status == BookingStatus.TICKETED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PillChip(stringResource(R.string.trips_pdf), selected = false, onClick = { onPdf(false) }, icon = JatriIcons.Download, enabled = !state.pdfBusy)
                        PillChip(stringResource(R.string.trips_share), selected = false, onClick = { onPdf(true) }, icon = JatriIcons.Share, enabled = !state.pdfBusy)
                        PillChip(stringResource(R.string.trips_calendar), selected = false, onClick = onCalendar, icon = JatriIcons.Calendar)
                    }
                }
                state.error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Timeline(b, state)
                if (state.canCancel) {
                    JatriTextButton(text = stringResource(R.string.trips_cancel_button), onClick = onCancel, color = JatriTheme.colors.danger)
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
    state.cancel?.let { CancelSheet(it, onDismissCancel, onConfirmCancel) }
}

@Composable
private fun TicketTop(b: Booking, state: TicketDetailUiState) {
    val lang = JatriTheme.language
    Text(tripTitle(b, state.places), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(Spacing.xs))
    if (b.isFlight) {
        BookedFlights(b, places = { state.places[it] })
    } else {
        val seg = b.segments.first()
        Text(
            DateTimeFormats.shortDate(seg.departAt.toLocalDate(), lang) + " · " +
                DateTimeFormats.time(seg.departAt, lang) + " – " + DateTimeFormats.time(seg.arriveAt, lang),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("${seg.carrier} · ${seg.number.localizeKeepingCodes(lang)}", style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
        if (b.mode == TravelMode.TRAIN) {
            val klass = com.jatri.core.model.TrainClass.entries.firstOrNull { it.name.equals(seg.travelClass, true) }
            Text(
                stringResource(R.string.trips_class_coach, klass?.label() ?: seg.travelClass, seg.coach.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        seg.boardingPoint?.let {
            Text(stringResource(R.string.trips_boarding, it.name(lang), DateTimeFormats.time(it.time, lang)), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(Spacing.xs))
    b.passengers.forEach { p ->
        val seat = p.seatNo?.let { "$it · " }.orEmpty()
        Text(seat + p.fullName + if (b.isFlight) " · ${p.type.label()}" else "", style = MaterialTheme.typography.titleSmall)
        p.ticketNumber?.let {
            Text(stringResource(R.string.trips_ticket_no, it.localizeKeepingCodes(lang)), style = MaterialTheme.typography.labelSmall, color = JatriTheme.colors.textSecondary)
        }
    }
}

private data class Step(val title: String, val detail: String?, val done: Boolean, val color: Color? = null)

/** Booked → paid & ticketed → journey, or → cancelled → refund on its way → refunded. */
@Composable
private fun Timeline(b: Booking, state: TicketDetailUiState) {
    val lang = JatriTheme.language
    fun at(i: Instant?) = i?.atZone(ZoneId.of("Asia/Dhaka"))?.toLocalDateTime()?.let {
        DateTimeFormats.shortDate(it.toLocalDate(), lang) + ", " + DateTimeFormats.time(it, lang)
    }
    val steps = buildList {
        add(Step(stringResource(R.string.trips_step_booked), b.ref, true))
        when (b.status) {
            BookingStatus.HELD, BookingStatus.PENDING -> add(Step(stringResource(R.string.trips_step_unpaid), null, false, JatriTheme.colors.warning))
            BookingStatus.FAILED -> add(Step(stringResource(R.string.trips_step_failed), null, true, JatriTheme.colors.danger))
            // Paid also when the ticket was later cancelled and refunded.
            else -> add(Step(stringResource(R.string.trips_step_ticketed), at(b.ticket?.issuedAt), b.ticket != null || b.refund != null || b.cancelledAt != null))
        }
        if (b.cancelledAt != null || b.status == BookingStatus.CANCELLED) {
            add(Step(stringResource(R.string.trips_step_cancelled), at(b.cancelledAt), true, JatriTheme.colors.danger))
        }
        val r = b.refund
        if (r != null) {
            val done = r.status == RefundStatus.REFUNDED
            add(
                Step(
                    stringResource(if (done) R.string.trips_step_refunded else R.string.trips_step_refunding, r.amount.format(lang)),
                    if (done) at(r.processedAt) else stringResource(R.string.trips_refund_eta),
                    done,
                    JatriTheme.colors.success,
                ),
            )
        } else if (b.status == BookingStatus.CANCELLED && b.ticket != null) {
            add(Step(stringResource(R.string.trips_step_no_refund), null, true))
        }
        if (b.status == BookingStatus.TICKETED) {
            val dep = b.departAt
            add(
                Step(
                    stringResource(R.string.trips_step_journey),
                    dep?.let { DateTimeFormats.shortDate(it.toLocalDate(), lang) + ", " + DateTimeFormats.time(it, lang) },
                    dep?.isBefore(state.now) == true,
                ),
            )
        }
    }
    JatriCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.trips_timeline), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        steps.forEachIndexed { i, s ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(12.dp)
                            .background(if (s.done) (s.color ?: MaterialTheme.colorScheme.primary) else JatriTheme.colors.divider, CircleShape),
                    )
                    if (i < steps.lastIndex) Box(Modifier.width(2.dp).height(30.dp).background(JatriTheme.colors.divider))
                }
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(s.title, style = MaterialTheme.typography.titleSmall, color = if (s.done) MaterialTheme.colorScheme.onSurface else JatriTheme.colors.textSecondary)
                    s.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary) }
                }
            }
        }
    }
}

/** Refund preview from the fare rules, then confirm. */
@Composable
private fun CancelSheet(sheet: CancelSheetState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val lang = JatriTheme.language
    JatriBottomSheet(title = stringResource(R.string.trips_cancel_title), onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.screen)) { CancelSheetBody(sheet, onDismiss, onConfirm) }
    }
}

@Composable
private fun CancelSheetBody(sheet: CancelSheetState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val lang = JatriTheme.language
    run {
        val q = sheet.quote
        when {
            sheet.loading -> Box(Modifier.fillMaxWidth().height(160.dp)) { LoadingState() }
            q == null -> Text(sheet.error?.text().orEmpty(), color = MaterialTheme.colorScheme.error)
            else -> {
                if (q.paid != Money.ZERO) {
                    Row2(stringResource(R.string.trips_cancel_paid), q.paid.format(lang))
                    Row2(stringResource(R.string.trips_cancel_fee), "− " + q.fee.format(lang), JatriTheme.colors.danger)
                    Row2(stringResource(R.string.trips_cancel_service), "− " + q.nonRefundable.format(lang), JatriTheme.colors.danger)
                    HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = JatriTheme.colors.divider)
                    Row2(stringResource(R.string.trips_cancel_refund), q.refund.format(lang), JatriTheme.colors.success, bold = true)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(q.message(lang), style = MaterialTheme.typography.bodyMedium)
                q.deadline?.takeIf { q.cancellable }?.let {
                    val t = it.atZone(ZoneId.of("Asia/Dhaka")).toLocalDateTime()
                    Text(
                        stringResource(R.string.trips_cancel_deadline, DateTimeFormats.shortDate(t.toLocalDate(), lang) + ", " + DateTimeFormats.time(t, lang)),
                        style = MaterialTheme.typography.bodySmall,
                        color = JatriTheme.colors.textSecondary,
                    )
                }
                sheet.error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(Spacing.md))
                if (q.cancellable) {
                    JatriCtaButton(
                        text = if (q.refund != Money.ZERO) {
                            stringResource(R.string.trips_cancel_confirm_refund, q.refund.format(lang))
                        } else {
                            stringResource(R.string.trips_cancel_confirm)
                        },
                        onClick = onConfirm,
                        loading = sheet.cancelling,
                    )
                }
                JatriTextButton(text = stringResource(R.string.trips_cancel_keep), onClick = onDismiss)
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun Row2(label: String, value: String, color: Color? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(value, style = style, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}
