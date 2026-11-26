package com.jatri.core.data.repository

import android.content.Context
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Money
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.PaymentStart
import com.jatri.core.model.PaymentState
import com.jatri.core.model.PaymentStatus
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.apiCall
import com.jatri.core.network.mock.MOCK_CHECKOUT_SCHEME
import com.jatri.core.network.mock.MockJatriApi
import com.jatri.core.network.model.PaymentInitRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface PaymentRepository {
    suspend fun start(bookingId: String, method: PaymentMethod, idempotencyKey: String): PaymentStart
    suspend fun status(paymentId: String): PaymentState

    /** True when [checkoutUrl] is the in-app demo gateway (no browser, no server). */
    fun isDemoCheckout(checkoutUrl: String): Boolean = checkoutUrl.startsWith(MOCK_CHECKOUT_SCHEME)

    /** Demo gateway only: what a real gateway's server callback would do. */
    suspend fun completeDemo(paymentId: String, success: Boolean)

    /** Downloads the PDF e-ticket once and keeps it for offline use. */
    suspend fun ticketPdf(bookingId: String): File
}

/**
 * The browser sends the user back via `jatri://payment/return?payment_id=…`. MainActivity posts
 * that here; the payment screen re-checks the status straight away.
 */
@Singleton
class PaymentReturnEvents @Inject constructor() {
    private val _returns = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val returns: SharedFlow<String> = _returns
    fun onReturn(paymentId: String) {
        _returns.tryEmit(paymentId)
    }
}

@Singleton
internal class NetworkPaymentRepository @Inject constructor(
    private val api: JatriApi,
    @ApplicationContext private val context: Context,
) : PaymentRepository {
    override suspend fun start(bookingId: String, method: PaymentMethod, idempotencyKey: String): PaymentStart {
        val dto = apiCall { api.initPayment(PaymentInitRequest(bookingId, method.name.lowercase()), idempotencyKey) }
        return PaymentStart(dto.paymentId, dto.checkoutUrl, Money(dto.amount))
    }

    override suspend fun status(paymentId: String): PaymentState {
        val dto = apiCall { api.paymentStatus(paymentId) }
        return PaymentState(
            paymentId = dto.paymentId,
            status = runCatching { PaymentStatus.valueOf(dto.status.uppercase()) }.getOrDefault(PaymentStatus.INITIATED),
            failureReason = dto.failureReason,
            bookingId = dto.bookingId,
            bookingStatus = runCatching { BookingStatus.valueOf(dto.bookingStatus.uppercase()) }.getOrDefault(BookingStatus.PENDING),
            bookingRef = dto.bookingRef,
        )
    }

    override suspend fun completeDemo(paymentId: String, success: Boolean) {
        (api as? MockJatriApi)?.completeDemoPayment(paymentId, success)
    }

    override suspend fun ticketPdf(bookingId: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "tickets").apply { mkdirs() }
        val file = File(dir, "Jatri-$bookingId.pdf")
        if (file.length() > 0) return@withContext file // already saved: works offline
        try {
            val tmp = File(dir, "$bookingId.part")
            api.ticketPdf(bookingId).use { body -> tmp.outputStream().use { body.byteStream().copyTo(it) } }
            if (!tmp.renameTo(file)) throw IOException("rename failed")
            file
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppException) {
            throw e
        } catch (e: retrofit2.HttpException) {
            throw AppException(AppError.Unknown)
        } catch (e: IOException) {
            throw AppException(AppError.Network)
        }
    }
}
