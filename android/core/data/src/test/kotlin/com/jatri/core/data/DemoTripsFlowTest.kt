package com.jatri.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jatri.core.data.repository.BookingCache
import com.jatri.core.data.repository.DefaultAuthRepository
import com.jatri.core.data.repository.NetworkBookingRepository
import com.jatri.core.data.repository.NetworkBusRepository
import com.jatri.core.data.repository.NetworkNotificationRepository
import com.jatri.core.data.repository.NetworkPaymentRepository
import com.jatri.core.data.repository.SeedLocationRepositoryForTest
import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.datastore.session.StoredSession
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.BusHoldRequest
import com.jatri.core.model.CoachType
import com.jatri.core.model.Gender
import com.jatri.core.model.Money
import com.jatri.core.model.PassengerInput
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.RefundStatus
import com.jatri.core.model.SeatState
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.mock.MockJatriApi
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.BookingDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Book → pay → quote → cancel → refunded → inbox, and tickets that open without internet. */
@RunWith(AndroidJUnit4::class)
class DemoTripsFlowTest {
    private class Store : SessionStore {
        val state = MutableStateFlow<StoredSession?>(null)
        override val session = state
        override suspend fun current() = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private val api = MockJatriApi()
    private val cacheFile = File.createTempFile("bookings", ".json").also { it.delete() }
    private val cache = BookingCache(cacheFile)
    private val bookings = NetworkBookingRepository(api, cache)
    private val payments = NetworkPaymentRepository(api, ApplicationProvider.getApplicationContext())
    private val inbox = NetworkNotificationRepository(api)

    @Test
    fun `cancel a ticket, get the refund, read the inbox, open it offline`() = runTest {
        DefaultAuthRepository(api, Store(), backgroundScope).verifyOtp("01712345678", MockJatriApi.MOCK_OTP)
        val buses = NetworkBusRepository(api, SeedLocationRepositoryForTest())
        val dhaka = SeedLocationRepositoryForTest().getById("bus_dhaka")!!
        val cox = SeedLocationRepositoryForTest().getById("bus_coxsbazar")!!
        val trip = buses.search(dhaka, cox, LocalDate.now().plusDays(5), CoachType.ANY).first()
        val map = buses.seatMap(trip.id)
        val seat = map.layout.rows.flatten().filterNotNull().first { it.state == SeatState.AVAILABLE }.number
        val held = bookings.createBusHold(
            BusHoldRequest(trip.id, listOf(seat), trip.boardingPoints[0].id, trip.droppingPoints[0].id,
                listOf(PassengerInput("Rahim", Gender.MALE, 30, seat)), "01712345678", null, trip.price + map.serviceChargePerSeat, "k"),
        )
        payments.completeDemo(payments.start(held.id, PaymentMethod.BKASH, "p").paymentId, success = true)
        assertEquals(BookingStatus.TICKETED, bookings.get(held.id).status)

        val quote = bookings.cancelQuote(held.id)
        assertTrue(quote.cancellable)
        assertEquals(quote.paid, quote.refund + quote.fee + quote.nonRefundable)
        assertTrue(quote.fee > Money.ZERO && quote.nonRefundable == held.serviceCharge)

        val wrong = runCatching { bookings.cancel(held.id, quote.refund + Money.taka(1)) }.exceptionOrNull() as AppException
        assertEquals("refund_changed", (wrong.error as AppError.Api).code)
        val done = bookings.cancel(held.id, quote.refund)
        assertEquals(BookingStatus.REFUNDED, done.status)
        assertEquals(RefundStatus.REFUNDED, done.refund!!.status)
        assertEquals(quote.refund, done.refund!!.amount)

        val items = inbox.inbox()
        assertEquals(listOf("refund_processed", "booking_cancelled", "booking_confirmed"), items.map { it.type })
        assertEquals(3, inbox.unread.value)
        inbox.markRead(items[0].id)
        assertEquals(2, inbox.unread.value)
        inbox.markAllRead()
        assertEquals(0, inbox.unread.value)

        // The list is saved on the phone; with no internet, the ticket still opens.
        assertEquals(listOf(held.id), bookings.list().map { it.id })
        val offline = NetworkBookingRepository(OfflineApi(api), BookingCache(cacheFile))
        assertEquals(BookingStatus.REFUNDED, offline.get(held.id).status)
        assertEquals(1, offline.cached().size)
        val failure = runCatching { offline.list() }.exceptionOrNull() as AppException
        assertEquals(AppError.Network, failure.error)
    }

    @Test
    fun `cache survives a new instance and can be cleared`() = runTest {
        val dto = BookingDto(
            id = "b1", bookingRef = "JTAAAA", mode = "bus", status = "ticketed", baseFare = 100, serviceCharge = 0, discount = 0,
            totalAmount = 100, currency = "BDT", contactPhone = "01712345678", segments = emptyList(), passengers = emptyList(),
            createdAt = "2026-11-27T00:00:00Z",
        )
        cache.replaceAll(listOf(dto))
        cache.put(dto.copy(status = "cancelled"))
        assertEquals(listOf("cancelled"), BookingCache(cacheFile).all().map { it.status })
        cache.clear()
        assertTrue(BookingCache(cacheFile).all().isEmpty())
    }
}

/** The server can't be reached. */
private class OfflineApi(real: JatriApi) : JatriApi by real {
    override suspend fun booking(id: String): ApiEnvelope<BookingDto> = throw java.io.IOException("no internet")
    override suspend fun bookings(): ApiEnvelope<List<BookingDto>> = throw java.io.IOException("no internet")
}
