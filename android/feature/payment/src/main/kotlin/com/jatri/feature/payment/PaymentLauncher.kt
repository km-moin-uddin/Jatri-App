package com.jatri.feature.payment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.jatri.core.network.mock.MOCK_CHECKOUT_SCHEME

/**
 * Opens the gateway's checkout page in a Chrome Custom Tab: the user sees the real gateway URL and
 * the app never touches card or wallet details. The gateway returns via jatri://payment/return.
 */
class PaymentLauncher(private val context: Context) {
    fun isDemo(url: String) = url.startsWith(MOCK_CHECKOUT_SCHEME)

    fun openCheckout(url: String) {
        val uri = url.toUri()
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
fun rememberPaymentLauncher(): PaymentLauncher {
    val context = LocalContext.current
    return remember(context) { PaymentLauncher(context) }
}
