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

private enum class PayMethod(val label: Int) { BKASH(R.string.pay_bkash), NAGAD(R.string.pay_nagad), ROCKET(R.string.pay_rocket), CARD(R.string.pay_card) }

private const val HOLD_SECONDS = 10 * 60

// MOCK price breakdown (2 bus seats). Real amounts come from the booking hold in Phase 3/4.
private val baseFare = Money.taka(2800)
private val taxes = Money.taka(0)
private val serviceCharge = Money.taka(60)
private val discount = Money.taka(150)
private val total = baseFare + taxes + serviceCharge - discount

@Composable
fun ReviewPayScreen(onBack: () -> Unit, onPaid: (success: Boolean) -> Unit) {
    val lang = JatriTheme.language
    var method by rememberSaveable { mutableStateOf(PayMethod.BKASH) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(HOLD_SECONDS) }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }
    Scaffold(
        topBar = { JatriTopAppBar(title = stringResource(R.string.pay_review_title), onBack = onBack) },
        bottomBar = {
            PriceSummaryBar(
                label = stringResource(R.string.pay_total),
                price = total.format(lang),
                buttonText = stringResource(R.string.pay_button, total.format(lang)),
                onClick = { onPaid(true) },
                enabled = agreed && secondsLeft > 0,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val mmss = "%02d:%02d".format(secondsLeft / 60, secondsLeft % 60).localizeDigits(lang)
            StatusChip(
                text = stringResource(R.string.pay_hold_timer, mmss),
                tone = if (secondsLeft < 120) StatusTone.Danger else StatusTone.Warning,
                icon = JatriIcons.Time,
            )
            PhaseBanner(stringResource(R.string.pay_phase))
            JatriCard {
                Text(stringResource(R.string.pay_price_details), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                PriceRow(stringResource(R.string.pay_base_fare), baseFare.format(lang))
                PriceRow(stringResource(R.string.pay_taxes), taxes.format(lang))
                PriceRow(stringResource(R.string.pay_service_charge), serviceCharge.format(lang))
                PriceRow(stringResource(R.string.pay_discount), (Money.ZERO - discount).format(lang), JatriTheme.colors.success)
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = JatriTheme.colors.divider)
                PriceRow(stringResource(R.string.pay_total), total.format(lang), bold = true)
            }
            JatriTextField(value = "JATRI150", onValueChange = {}, label = stringResource(R.string.pay_promo), enabled = false)
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
            JatriTextButton(
                text = stringResource(R.string.pay_simulate_failure),
                onClick = { onPaid(false) },
                color = JatriTheme.colors.textSecondary,
            )
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
