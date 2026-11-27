package com.jatri.core.ui.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.jatri.core.common.format.format
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.designsystem.component.CircleIconButton
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Money
import com.jatri.core.ui.R

/** Where the "tell me when it's cheaper" bell on a results screen is. */
enum class PriceAlertStatus { IDLE, SAVING, SAVED }

/** Default target: 10% under today's lowest fare, rounded down to ৳10. */
fun suggestedTarget(lowest: Money): Long = (lowest.paisa * 9 / 10 / 100 / 10 * 10).coerceAtLeast(10)

/** Parses "১২৩৪" or "1,234" into whole taka. */
fun parseTaka(text: String): Long? =
    text.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.filter { it.isDigit() }.joinToString("").toLongOrNull()

/**
 * Bell in the results top bar. Tapping it asks for a target price per person; the screen saves
 * a price alert and the server checks fares every 30 minutes.
 */
@Composable
fun PriceAlertAction(lowest: Money?, status: PriceAlertStatus, onCreate: (Money) -> Unit) {
    if (lowest == null) return
    val lang = JatriTheme.language
    var open by rememberSaveable { mutableStateOf(false) }
    CircleIconButton(
        icon = if (status == PriceAlertStatus.SAVED) JatriIcons.Notifications else JatriIcons.NotificationsOutlined,
        contentDescription = stringResource(R.string.ui_price_alert),
        selected = status == PriceAlertStatus.SAVED,
        onClick = { if (status == PriceAlertStatus.IDLE) open = true },
    )
    if (!open) return
    var text by rememberSaveable { mutableStateOf(suggestedTarget(lowest).toString().localizeDigits(lang)) }
    val taka = parseTaka(text)
    val valid = taka != null && taka > 0 && Money.taka(taka) < lowest
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.ui_price_alert_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.ui_price_alert_body, lowest.format(lang)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JatriTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Spacing.sm))
                JatriTextField(
                    value = text,
                    onValueChange = { text = it.take(7) },
                    label = stringResource(R.string.ui_price_alert_target),
                    error = if (taka != null && !valid) stringResource(R.string.ui_price_alert_too_high) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { open = false; onCreate(Money.taka(taka!!)) }) {
                Text(stringResource(R.string.ui_price_alert_save))
            }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ui_price_alert_cancel)) } },
    )
}


/** Shows "alert set" / "couldn't set" once, then tells the screen it was shown. */
@Composable
fun PriceAlertSnackbar(result: Boolean?, host: androidx.compose.material3.SnackbarHostState, onShown: () -> Unit) {
    val saved = stringResource(R.string.ui_price_alert_saved)
    val failed = stringResource(R.string.ui_price_alert_failed)
    androidx.compose.runtime.LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        onShown()
        host.showSnackbar(if (result) saved else failed)
    }
}
