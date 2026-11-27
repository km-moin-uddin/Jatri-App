package com.jatri.feature.payment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.common.format.localizeKeepingCodes
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.designsystem.component.JatriBottomSheet
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.JatriOutlinedButton
import com.jatri.core.designsystem.component.LoadingState
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.component.TicketCard
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Booking
import com.jatri.core.ui.flight.BookedFlights
import com.jatri.core.ui.flight.label
import com.jatri.core.model.Location
import com.jatri.core.model.Money
import com.jatri.core.ui.QrCode
import com.jatri.core.ui.text
import java.io.File

@Composable
internal fun PaymentProcessingRoute(
    onTicketed: (String) -> Unit,
    onFailed: () -> Unit,
    onGoHome: () -> Unit,
    onViewTrips: () -> Unit,
    viewModel: PaymentProcessingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.bind(onTicketed, onFailed) }
    val lang = JatriTheme.language
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state.phase) {
            ProcessingPhase.WAITING, ProcessingPhase.DEMO_CHOICE -> {
                CircularProgressIndicator(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Spacing.lg))
                Text(stringResource(R.string.pay_processing_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.pay_processing_body), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary, textAlign = TextAlign.Center)
            }
            ProcessingPhase.SLOW -> Message(JatriIcons.Time, stringResource(R.string.pay_slow_title), stringResource(R.string.pay_slow_body)) {
                JatriCtaButton(stringResource(R.string.pay_check_again), onClick = viewModel::checkAgain)
                Spacer(Modifier.height(Spacing.xs))
                JatriOutlinedButton(stringResource(R.string.pay_view_trips), onClick = onViewTrips)
            }
            ProcessingPhase.REFUND -> Message(JatriIcons.Error, stringResource(R.string.pay_refund_title), stringResource(R.string.pay_refund_body)) {
                JatriCtaButton(stringResource(R.string.pay_go_home), onClick = onGoHome)
            }
        }
    }
    if (state.phase == ProcessingPhase.DEMO_CHOICE) {
        // Sandbox stand-in for the gateway page when there is no server (demo builds).
        JatriBottomSheet(title = stringResource(R.string.pay_demo_gateway_title), onDismiss = { viewModel.demoAnswer(false) }) {
            Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg)) {
                StatusChip("SANDBOX", StatusTone.Warning)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.pay_demo_gateway_body), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
                Spacer(Modifier.height(Spacing.md))
                JatriCtaButton(stringResource(R.string.pay_demo_pay, Money(state.amountPaisa).format(lang)), onClick = { viewModel.demoAnswer(true) })
                Spacer(Modifier.height(Spacing.xs))
                JatriOutlinedButton(stringResource(R.string.pay_demo_fail), onClick = { viewModel.demoAnswer(false) })
            }
        }
    }
}

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, actions: @Composable () -> Unit) {
    Box(Modifier.size(88.dp).background(JatriTheme.colors.warningContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = JatriTheme.colors.warning, modifier = Modifier.size(44.dp))
    }
    Spacer(Modifier.height(Spacing.lg))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Spacing.xs))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Spacing.lg))
    actions()
}

@Composable
internal fun PaymentResultRoute(
    success: Boolean,
    onRetry: () -> Unit,
    onViewTrips: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: TicketViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val booking = state.booking
    when {
        state.loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LoadingState() }
        success && booking != null -> TicketResultScreen(
            booking = booking,
            from = state.from,
            to = state.to,
            pdfBusy = state.pdfBusy,
            onOpenPdf = { viewModel.pdf { openPdf(context, it, share = false) } },
            onSharePdf = { viewModel.pdf { openPdf(context, it, share = true) } },
            onViewTrips = onViewTrips,
            onGoHome = onGoHome,
        )
        else -> PaymentResultScreen(success = success, onRetry = onRetry, onViewTrips = onViewTrips, onGoHome = onGoHome)
    }
    state.error?.takeIf { booking != null }?.let { e ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(e.text()) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.pay_ok)) } },
        )
    }
}

/** Success screen with the real e-ticket: route, times, seats, ticket numbers and the signed QR. */
@Composable
fun TicketResultScreen(
    booking: Booking,
    from: Location?,
    to: Location?,
    pdfBusy: Boolean,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onViewTrips: () -> Unit,
    onGoHome: () -> Unit,
) {
    val lang = JatriTheme.language
    val seg = booking.segments.firstOrNull()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(JatriTheme.colors.successContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(JatriIcons.CheckCircle, contentDescription = null, tint = JatriTheme.colors.success, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(R.string.pay_success_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.lg))
        TicketCard(
            modifier = Modifier.fillMaxWidth(),
            notchOffsetFraction = 0.5f,
            top = {
                Text(
                    "${from?.city(lang) ?: seg?.fromId.orEmpty()} ${if (booking.isFlight && booking.journeys.size == 2) "⇄" else "→"} ${to?.city(lang) ?: seg?.toId.orEmpty()}",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (booking.isFlight) {
                    Spacer(Modifier.height(Spacing.xs))
                    BookedFlights(booking, places = { null })
                    Spacer(Modifier.height(Spacing.xs))
                    booking.passengers.forEach { p ->
                        Text("${p.fullName} · ${p.type.label()}", style = MaterialTheme.typography.titleSmall)
                        p.ticketNumber?.let {
                            Text(stringResource(R.string.pay_eticket_no, it.localizeKeepingCodes(lang)), style = MaterialTheme.typography.labelSmall, color = JatriTheme.colors.textSecondary)
                        }
                    }
                } else {
                    if (seg != null) {
                        Text(
                            DateTimeFormats.shortDate(seg.departAt.toLocalDate(), lang) + " · " + DateTimeFormats.time(seg.departAt, lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = JatriTheme.colors.textSecondary,
                        )
                        Text("${seg.carrier} · ${seg.number}", style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
                        if (booking.mode == com.jatri.core.model.TravelMode.TRAIN) {
                            Text(
                                stringResource(R.string.pay_class_coach, trainClassLabel(seg.travelClass), seg.coach.orEmpty()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        seg.boardingPoint?.let {
                            Text(it.name(lang) + " · " + DateTimeFormats.time(it.time, lang), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    booking.passengers.forEach { p ->
                        Text("${p.seatNo.orEmpty()} · ${p.fullName}", style = MaterialTheme.typography.titleSmall)
                        p.ticketNumber?.let { Text(stringResource(R.string.pay_ticket_no, it), style = MaterialTheme.typography.labelSmall, color = JatriTheme.colors.textSecondary) }
                    }
                }
            },
            bottom = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pay_pnr), style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
                            Text(booking.ref, style = MaterialTheme.typography.headlineSmall)
                        }
                        Text(booking.total.format(lang), style = MaterialTheme.typography.titleLarge)
                    }
                    booking.ticket?.let {
                        Spacer(Modifier.height(Spacing.sm))
                        QrCode(payload = it.qrPayload, contentDescription = stringResource(R.string.pay_qr))
                    }
                }
            },
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PillChip(stringResource(R.string.pay_download_pdf), selected = false, onClick = onOpenPdf, icon = JatriIcons.Download, enabled = !pdfBusy)
            PillChip(stringResource(R.string.pay_share), selected = false, onClick = onSharePdf, icon = JatriIcons.Share, enabled = !pdfBusy)
        }
        Spacer(Modifier.height(Spacing.lg))
        JatriCtaButton(text = stringResource(R.string.pay_view_trips), onClick = onViewTrips)
        Spacer(Modifier.height(Spacing.xs))
        JatriOutlinedButton(text = stringResource(R.string.pay_go_home), onClick = onGoHome)
    }
}

private fun openPdf(context: Context, file: File, share: Boolean) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = if (share) {
        Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
    } else {
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(if (share) Intent.createChooser(intent, null) else intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.pay_no_pdf_app, Toast.LENGTH_LONG).show()
    }
}
