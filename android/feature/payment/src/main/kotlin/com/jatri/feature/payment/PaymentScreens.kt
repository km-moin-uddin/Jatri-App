package com.jatri.feature.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.common.format.DateTimeFormats
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.LoadingState
import com.jatri.core.model.Booking
import com.jatri.core.ui.flight.BookedFlights
import com.jatri.core.ui.flight.FareRulesCard
import com.jatri.core.ui.flight.label
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.PaymentStart
import com.jatri.core.ui.text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.JatriOutlinedButton
import com.jatri.core.designsystem.component.JatriTextButton
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.component.PriceSummaryBar
import androidx.compose.foundation.layout.statusBarsPadding
import com.jatri.core.designsystem.component.StatusChip
import com.jatri.core.designsystem.component.StatusTone
import com.jatri.core.designsystem.component.TicketCard
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Money
import com.jatri.core.ui.placeholder.PhaseBanner
import kotlinx.coroutines.delay

private enum class PayMethod(val label: Int, val model: PaymentMethod) {
    BKASH(R.string.pay_bkash, PaymentMethod.BKASH),
    NAGAD(R.string.pay_nagad, PaymentMethod.NAGAD),
    ROCKET(R.string.pay_rocket, PaymentMethod.ROCKET),
    CARD(R.string.pay_card, PaymentMethod.CARD),
}

// SAMPLE numbers for the flight/train preview (no booking yet). Bus bookings show real ones.
private val sampleBase = Money.taka(2800)
private val sampleService = Money.taka(60)
private val sampleDiscount = Money.taka(150)
private const val SAMPLE_HOLD_SECONDS = 10 * 60

@Composable
internal fun ReviewPayRoute(
    onBack: () -> Unit,
    onPaid: (success: Boolean) -> Unit,
    onPaymentStarted: (PaymentStart, demo: Boolean) -> Unit,
    onHoldGone: () -> Unit,
    viewModel: ReviewPayViewModel = hiltViewModel(),
    payments: PaymentLauncher = rememberPaymentLauncher(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state.payError?.let { e ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPayError,
            text = { Text(e.text()) },
            confirmButton = { TextButton(onClick = viewModel::dismissPayError) { Text(stringResource(R.string.pay_try_again)) } },
        )
    }
    if (viewModel.bookingId == null) {
        ReviewPayScreen(onBack = onBack, onPaid = onPaid)
        return
    }
    val booking = state.booking
    when {
        state.loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LoadingState() }
        booking == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            ErrorState(onRetry = viewModel::retry, message = state.error?.text())
        }
        else -> ReviewPayScreen(
            onBack = onBack,
            onPaid = onPaid,
            booking = booking,
            secondsLeft = state.secondsLeft,
            onCancel = { viewModel.cancel(onHoldGone) },
            cancelling = state.cancelling,
            paying = state.paying,
            onPay = { method ->
                viewModel.pay(method) { start ->
                    val demo = payments.isDemo(start.checkoutUrl)
                    if (!demo) payments.openCheckout(start.checkoutUrl)
                    onPaymentStarted(start, demo)
                }
            },
        )
    }
    if (state.expired && booking != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.pay_hold_expired_title)) },
            text = { Text(stringResource(R.string.pay_hold_expired_body)) },
            confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.pay_search_again)) } },
        )
    }
}

/**
 * Review & pay. With a [booking] it shows the real hold: trip, passengers, price breakdown and the
 * server's countdown. Payment itself is still simulated until the gateway sandbox (Phase 4).
 */
@Composable
fun ReviewPayScreen(
    onBack: () -> Unit,
    onPaid: (success: Boolean) -> Unit,
    booking: Booking? = null,
    secondsLeft: Int? = null,
    onCancel: (() -> Unit)? = null,
    cancelling: Boolean = false,
    onPay: ((PaymentMethod) -> Unit)? = null,
    paying: Boolean = false,
) {
    val lang = JatriTheme.language
    var method by rememberSaveable { mutableStateOf(PayMethod.BKASH) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var sampleLeft by rememberSaveable { mutableIntStateOf(SAMPLE_HOLD_SECONDS) }
    if (booking == null) {
        LaunchedEffect(sampleLeft) {
            if (sampleLeft > 0) {
                delay(1_000)
                sampleLeft -= 1
            }
        }
    }
    val left = secondsLeft ?: sampleLeft
    val base = booking?.baseFare ?: sampleBase
    val service = booking?.serviceCharge ?: sampleService
    val discount = booking?.discount ?: sampleDiscount
    val total = booking?.total ?: (sampleBase + sampleService - sampleDiscount)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            JatriTopAppBar(
                title = stringResource(R.string.pay_review_title),
                subtitle = booking?.let { stringResource(R.string.pay_ref, it.ref) },
                onBack = onBack,
            )
        },
        bottomBar = {
            PriceSummaryBar(
                label = stringResource(R.string.pay_total),
                price = total.format(lang),
                buttonText = stringResource(R.string.pay_button, total.format(lang)),
                onClick = { if (onPay != null) onPay(method.model) else onPaid(true) },
                enabled = agreed && left > 0,
                loading = paying,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val mmss = "%02d:%02d".format(left / 60, left % 60).localizeDigits(lang)
            StatusChip(
                text = stringResource(if (booking?.isFlight == true) R.string.pay_hold_timer_flight else R.string.pay_hold_timer, mmss),
                tone = if (left < 120) StatusTone.Danger else StatusTone.Warning,
                icon = JatriIcons.Time,
            )
            if (booking == null) {
                PhaseBanner(stringResource(R.string.pay_phase))
            } else {
                // Payments run on the gateway's sandbox: say so plainly, no "coming soon" banner.
                StatusChip(stringResource(R.string.pay_demo_note), StatusTone.Info, icon = JatriIcons.Info)
            }
            booking?.let { if (it.isFlight) FlightSummary(it) else TripSummary(it) }
            booking?.fareRules?.let { FareRulesCard(rules = it, brand = booking.fareBrand, cabin = booking.cabinClass) }
            JatriCard {
                Text(stringResource(R.string.pay_price_details), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                PriceRow(stringResource(R.string.pay_base_fare), base.format(lang))
                booking?.taxes?.takeIf { it != Money.ZERO }?.let { PriceRow(stringResource(R.string.pay_taxes), it.format(lang)) }
                PriceRow(stringResource(R.string.pay_service_charge), service.format(lang))
                if (discount != Money.ZERO) {
                    PriceRow(stringResource(R.string.pay_discount), (Money.ZERO - discount).format(lang), JatriTheme.colors.success)
                }
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = JatriTheme.colors.divider)
                PriceRow(stringResource(R.string.pay_total), total.format(lang), bold = true)
            }
            JatriTextField(value = "", onValueChange = {}, label = stringResource(R.string.pay_promo), enabled = false)
            JatriCard {
                Text(stringResource(R.string.pay_method), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    PayMethod.entries.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.touchTarget)
                                .selectable(selected = m == method, role = Role.RadioButton, onClick = { method = m }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = m == method, onClick = null)
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(m.label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touchTarget)
                    .toggleable(value = agreed, role = Role.Checkbox, onValueChange = { agreed = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = agreed, onCheckedChange = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.pay_terms), style = MaterialTheme.typography.bodyMedium)
            }
            if (onCancel != null) {
                JatriTextButton(
                    text = stringResource(R.string.pay_cancel_booking),
                    onClick = { if (!cancelling) onCancel() },
                    color = JatriTheme.colors.danger,
                )
            }
            if (onPay == null) {
                // Preview only. Real bookings use the gateway's own fail option.
                JatriTextButton(
                    text = stringResource(R.string.pay_simulate_failure),
                    onClick = { onPaid(false) },
                    color = JatriTheme.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun FlightSummary(booking: Booking) {
    val lang = JatriTheme.language
    JatriCard {
        Text(stringResource(R.string.pay_flights), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        BookedFlights(booking, places = { null })
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = JatriTheme.colors.divider)
        Text(stringResource(R.string.pay_passengers), style = MaterialTheme.typography.titleSmall)
        booking.passengers.forEach { p ->
            Text("${p.fullName} · ${p.type.label()}", style = MaterialTheme.typography.bodyMedium)
            p.documentMasked?.let {
                Text(stringResource(R.string.pay_document, it.localizeDigits(lang)), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun TripSummary(booking: Booking) {
    val lang = JatriTheme.language
    val seg = booking.segments.firstOrNull() ?: return
    JatriCard {
        Text(stringResource(R.string.pay_trip), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text("${seg.carrier} · ${seg.number}", style = MaterialTheme.typography.bodyLarge)
        Text(
            DateTimeFormats.shortDate(seg.departAt.toLocalDate(), lang) + " · " +
                DateTimeFormats.time(seg.departAt, lang) + " – " + DateTimeFormats.time(seg.arriveAt, lang),
            style = MaterialTheme.typography.bodyMedium,
            color = JatriTheme.colors.textSecondary,
        )
        if (seg.boardingPoint != null && seg.droppingPoint != null) {
            Text(
                stringResource(R.string.pay_board_drop, seg.boardingPoint!!.name(lang), seg.droppingPoint!!.name(lang)),
                style = MaterialTheme.typography.bodyMedium,
                color = JatriTheme.colors.textSecondary,
            )
        }
        Text(stringResource(R.string.pay_seats, seg.seatNumbers.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = JatriTheme.colors.divider)
        Text(stringResource(R.string.pay_passengers), style = MaterialTheme.typography.titleSmall)
        booking.passengers.forEach { p ->
            Text("${p.seatNo.orEmpty()} · ${p.fullName}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(value, style = style, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun PaymentResultScreen(success: Boolean, onRetry: () -> Unit, onViewTrips: () -> Unit, onGoHome: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        PaymentResultContent(success, onRetry, onViewTrips, onGoHome)
    }
}

@Composable
private fun PaymentResultContent(success: Boolean, onRetry: () -> Unit, onViewTrips: () -> Unit, onGoHome: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xl))
        val tint = if (success) JatriTheme.colors.success else JatriTheme.colors.danger
        val bg = if (success) JatriTheme.colors.successContainer else JatriTheme.colors.dangerContainer
        Box(Modifier.size(96.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (success) JatriIcons.CheckCircle else JatriIcons.Error, contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(if (success) R.string.pay_success_title else R.string.pay_failure_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(if (success) R.string.pay_success_body else R.string.pay_failure_body),
            style = MaterialTheme.typography.bodyMedium,
            color = JatriTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        if (success) {
            SampleTicket()
            Spacer(Modifier.height(Spacing.lg))
            JatriCtaButton(text = stringResource(R.string.pay_view_trips), onClick = onViewTrips)
            Spacer(Modifier.height(Spacing.xs))
            JatriOutlinedButton(text = stringResource(R.string.pay_go_home), onClick = onGoHome)
        } else {
            JatriCtaButton(text = stringResource(R.string.pay_try_again), onClick = onRetry)
            Spacer(Modifier.height(Spacing.xs))
            JatriOutlinedButton(text = stringResource(R.string.pay_go_home), onClick = onGoHome)
        }
    }
}

@Composable
private fun SampleTicket() {
    val lang = JatriTheme.language
    TicketCard(
        modifier = Modifier.fillMaxWidth(),
        top = {
            StatusChip(stringResource(R.string.pay_sample_ticket), StatusTone.Info)
            Spacer(Modifier.height(Spacing.xs))
            Text(stringResource(R.string.pay_sample_route), style = MaterialTheme.typography.titleLarge)
            Text("Sample Paribahan · A3, A4", style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
        },
        bottom = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pay_pnr), style = MaterialTheme.typography.labelMedium, color = JatriTheme.colors.textSecondary)
                    Text("JT8K2Q", style = MaterialTheme.typography.titleLarge)
                }
                // Real QR (ZXing) arrives with e-tickets in Phase 4.
                Icon(JatriIcons.QrCode, contentDescription = stringResource(R.string.pay_qr), modifier = Modifier.size(64.dp))
            }
        },
    )
}
