package com.jatri.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jatri.core.data.repository.DefaultAuthRepository
import com.jatri.core.data.repository.FlightSearchLeg
import com.jatri.core.data.repository.NetworkBookingRepository
import com.jatri.core.data.repository.NetworkFlightRepository
import com.jatri.core.data.repository.NetworkPaymentRepository
import com.jatri.core.data.repository.SeedLocationRepositoryForTest
import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.datastore.session.StoredSession
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.CabinClass
import com.jatri.core.model.FlightHoldRequest
import com.jatri.core.model.FlightPassengerInput
import com.jatri.core.model.Gender
import com.jatri.core.model.PassengerType
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.PaymentStatus
import com.jatri.core.network.mock.MockJatriApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The demo APK's flight flow: search → live offer → hold (PNR) → pay on the demo gateway → e-tickets. */
@RunWith(AndroidJUnit4::class)
class DemoFlightFlowTest {
    private class Store : SessionStore {
        val state = MutableStateFlow<StoredSession?>(null)
        override val session = state
        override suspend fun current() = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private val api = MockJatriApi()
    private val flights = NetworkFlightRepository(api, SeedLocationRepositoryForTest())
    private val bookings = NetworkBookingRepository(api, com.jatri.core.data.repository.BookingCache(ApplicationProvider.getApplicationContext()))
    private val payments = NetworkPaymentRepository(api, ApplicationProvider.getApplicationContext())
    private val day = LocalDate.now().plusDays(15)

    @Before
    fun login() = runTest {
        DefaultAuthRepository(api, Store(), backgroundScope).verifyOtp("01712345678", MockJatriApi.MOCK_OTP)
    }

    private fun adult(doc: String? = null, expiry: LocalDate? = null) =
        FlightPassengerInput(PassengerType.ADULT, "Rahim", "Uddin", Gender.MALE, LocalDate.of(1990, 5, 1), documentNo = doc, documentExpiry = expiry)

    private suspend fun expectCode(code: String, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $code")
        } catch (e: AppException) {
            assertEquals(code, (e.error as AppError.Api).code)
        }
    }

    @Test
    fun `domestic one way with an infant, end to end`() = runTest {
        val offers = flights.search(listOf(FlightSearchLeg("air_DAC", "air_CXB", day)), 1, 0, 1, CabinClass.ECONOMY)
        assertTrue(offers.size >= 6)
        assertTrue(offers.any { it.rules.refundable } && offers.any { !it.rules.refundable })
        val offer = flights.offer(offers.first().id)
        assertEquals(offers.first().price, offer.price)

        val infant = FlightPassengerInput(PassengerType.INFANT, "Ayan", "Uddin", Gender.MALE, day.minusMonths(10))
        val held = flights.hold(FlightHoldRequest(offer.id, listOf(adult(), infant), "01712345678", null, offer.price, "f1"))
        assertEquals(BookingStatus.HELD, held.status)
        assertEquals(offer.price, held.total)
        assertEquals(listOf("RAHIM UDDIN", "AYAN UDDIN"), held.passengers.map { it.fullName })
        // Retrying the same tap returns the same hold.
        assertEquals(held.id, flights.hold(FlightHoldRequest(offer.id, listOf(adult(), infant), "01712345678", null, offer.price, "f1")).id)

        val start = payments.start(held.id, PaymentMethod.NAGAD, "p1")
        payments.completeDemo(start.paymentId, success = true)
        assertEquals(PaymentStatus.SUCCESS, payments.status(start.paymentId).status)
        val done = bookings.get(held.id)
        assertEquals(BookingStatus.TICKETED, done.status)
        assertTrue(done.passengers.all { it.ticketNumber!!.matches(Regex("^000-\\d{10}$")) })
    }

    @Test
    fun `round trip and multi-city searches`() = runTest {
        val rt = flights.search(
            listOf(FlightSearchLeg("air_DAC", "air_KUL", day), FlightSearchLeg("air_KUL", "air_DAC", day.plusDays(6))), 2, 1, 0, CabinClass.BUSINESS,
        )
        assertTrue(rt.isNotEmpty() && rt.all { it.journeys.size == 2 && it.international })
        assertTrue(rt.all { it.rules.checkedBaggageKg == 40 })
        val mc = flights.search(
            listOf(
                FlightSearchLeg("air_DAC", "air_CXB", day),
                FlightSearchLeg("air_CXB", "air_CGP", day.plusDays(2)),
                FlightSearchLeg("air_CGP", "air_ZYL", day.plusDays(4)),
            ),
            1, 0, 0, CabinClass.ECONOMY,
        )
        assertTrue(mc.isNotEmpty() && mc.all { it.journeys.size == 3 })
        // No direct flight from a regional airport to another: connect through Dhaka.
        val via = flights.search(listOf(FlightSearchLeg("air_JSR", "air_ZYL", day)), 1, 0, 0, CabinClass.ECONOMY)
        assertTrue(via.all { it.first.stops == 1 && it.first.legs.first().to.code == "DAC" })
    }

    @Test
    fun `international trips check passports and the server re-checks the price`() = runTest {
        val offer = flights.search(listOf(FlightSearchLeg("air_DAC", "air_DXB", day)), 1, 0, 0, CabinClass.ECONOMY).first()
        expectCode("passport_required") { flights.hold(FlightHoldRequest(offer.id, listOf(adult()), "01712345678", null, offer.price, "a")) }
        expectCode("passport_expiry") {
            flights.hold(FlightHoldRequest(offer.id, listOf(adult("A1234567", day.plusMonths(2))), "01712345678", null, offer.price, "b"))
        }
        expectCode("price_changed") {
            flights.hold(FlightHoldRequest(offer.id, listOf(adult("A1234567", day.plusYears(2))), "01712345678", null, offer.price + offer.price, "c"))
        }
        val ok = flights.hold(FlightHoldRequest(offer.id, listOf(adult("A1234567", day.plusYears(2))), "01712345678", null, offer.price, "d"))
        assertEquals(BookingStatus.HELD, ok.status)
        expectCode("offer_expired") { flights.offer("mf~nonsense") }
    }
}
