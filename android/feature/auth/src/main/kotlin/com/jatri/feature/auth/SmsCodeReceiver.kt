package com.jatri.feature.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

/**
 * Listens for the OTP SMS with the SMS Retriever API (no SMS permission needed). The SMS must end
 * with this app's 11-character hash (backend: JATRI_ANDROID_APP_HASH). Times out after 5 minutes.
 */
@Composable
internal fun SmsCodeListener(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onCode)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras = intent.extras ?: return
                val status = if (Build.VERSION.SDK_INT >= 33) {
                    extras.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.get(SmsRetriever.EXTRA_STATUS) as? Status
                }
                if (status?.statusCode != CommonStatusCodes.SUCCESS) return
                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE).orEmpty()
                extractOtp(message)?.let { latest.value(it) }
            }
        }
        runCatching { SmsRetriever.getClient(context).startSmsRetriever() }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}

/** First run of exactly 6 digits in the message. */
internal fun extractOtp(message: String): String? = Regex("(?<!\\d)(\\d{6})(?!\\d)").find(message)?.groupValues?.get(1)
