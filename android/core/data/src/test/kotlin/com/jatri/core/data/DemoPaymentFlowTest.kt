package com.jatri.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jatri.core.data.repository.DefaultAuthRepository
import com.jatri.core.data.repository.NetworkBookingRepository
import com.jatri.core.data.repository.NetworkBusRepository
import com.jatri.core.data.repository.NetworkPaymentRepository
import com.jatri.core.data.repository.SeedLocationRepositoryForTest
import com.jatri.core.data.seed.MockContent
import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.datastore.session.StoredSession
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.BusHoldRequest
import com.jatri.core.model.CoachType
import com.jatri.core.model.Gender
import com.jatri.core.model.PassengerInput
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.PaymentStatus
import com.jatri.core.model.SeatState
import com.jatri.core.network.mock.MockJatriApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** search → seats → hold → pay on the demo gateway → ticketed, all through the real repositories. */
@RunWith(AndroidJUnit4::class)
class DemoPaymentFlowTest {
    private class Store : SessionStore {
        val state = MutableStateFlow<StoredSession?>(null)
        override val session = state
        override suspend fun current() = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private val api = MockJatriApi()
    private val locations = SeedLocationRepositoryForTest()
    private val buses = NetworkBusRepository(api, locations)
    private val bookings = NetworkBookingRepository(api)
    private val payments = NetworkPaymentRepository(api, ApplicationProvider.getApplicationContext())

    @Test
    fun `book and pay a bus ticket end to end`() = runTest {
        DefaultAuthRepository(api, Store(), backgroundScope).verifyOtp("01712345678", MockJatriApi.MOCK_OTP)
        val route = MockContent.popularRoutes.first()
        val trip = buses.search(route.from, route.to, LocalDate.now().plusDays(2), CoachType.ANY).first()
        val map = buses.seatMap(trip.id)
        val seat = map.layout.rows.flatten().filterNotNull().first { it.state == SeatState.AVAILABLE }.number

        val held = bookings.createBusHold(
            BusHoldRequest(
                tripId = trip.id, seats = listOf(seat),
                boardingPointId = map.trip.boardingPoints.first().id, droppingPointId = map.trip.droppingPoints.first().id,
                passengers = listOf(PassengerInput("Rahim", Gender.MALE, 30, seat)),
                contactPhone = "01712345678", contactEmail = null,
                expectedTotal = map.trip.price + map.serviceChargePerSeat, idempotencyKey = "k1",
            ),
        )
        assertEquals(BookingStatus.HELD, held.status)

        val start = payments.start(held.id, PaymentMethod.BKASH, "p1")
        assertTrue(payments.isDemoCheckout(start.checkoutUrl))
        assertEquals(held.total, start.amount)
        assertEquals(PaymentStatus.INITIATED, payments.status(start.paymentId).status)

        payments.completeDemo(start.paymentId, success = true)
        val st = payments.status(start.paymentId)
        assertEquals(PaymentStatus.SUCCESS, st.status)
        assertEquals(BookingStatus.TICKETED, st.bookingStatus)

        val ticketed = bookings.get(held.id)
        assertNotNull(ticketed.ticket)
        assertTrue(ticketed.passengers.single().ticketNumber!!.startsWith("PNR-"))

        // The demo backend can't render PDFs; the error is a friendly bilingual one.
        try {
            payments.ticketPdf(held.id)
            fail("expected AppException")
        } catch (e: AppException) {
            assertEquals("pdf_unavailable", (e.error as AppError.Api).code)
        }
    }
}
