package com.jatri.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jatri.core.data.repository.BookingCache
import com.jatri.core.data.repository.DefaultAuthRepository
import com.jatri.core.data.repository.NetworkBookingRepository
import com.jatri.core.data.repository.NetworkBusRepository
import com.jatri.core.data.repository.NetworkPaymentRepository
import com.jatri.core.data.repository.NetworkPriceAlertRepository
import com.jatri.core.data.repository.NetworkTrainRepository
import com.jatri.core.data.repository.NetworkTravellerRepository
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
import com.jatri.core.model.SavedTraveller
import com.jatri.core.model.SeatState
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TrainHoldRequest
import com.jatri.core.model.TravelMode
import com.jatri.core.network.mock.MockJatriApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Phase 8 in the demo APK: promo codes on a held booking, saved travellers, price alerts. */
@RunWith(AndroidJUnit4::class)
class DemoEngagementFlowTest {
    private class Store : SessionStore {
        val state = MutableStateFlow<StoredSession?>(null)
        override val session = state
        override suspend fun current() = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private val api = MockJatriApi()
    private val locations = SeedLocationRepositoryForTest()
    private val bookings = NetworkBookingRepository(api, BookingCache(File.createTempFile("bookings", ".json").also { it.delete() }))
    private val payments = NetworkPaymentRepository(api, ApplicationProvider.getApplicationContext())
    private val today = LocalDate.now(ZoneId.of("Asia/Dhaka"))

    private suspend fun code(block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (e: AppException) {
        (e.error as AppError.Api).code
    }

    @Test
    fun `promo codes change the price and follow their rules`() = runTest {
        DefaultAuthRepository(api, Store(), backgroundScope).verifyOtp("01712345678", MockJatriApi.MOCK_OTP)
        val buses = NetworkBusRepository(api, locations)
        val trip = buses.search(locations.getById("bus_dhaka")!!, locations.getById("bus_coxsbazar")!!, today.plusDays(5), CoachType.ANY).first()
        val map = buses.seatMap(trip.id)
        val seat = map.layout.rows.flatten().filterNotNull().first { it.state == SeatState.AVAILABLE }.number
        val held = bookings.createBusHold(
            BusHoldRequest(trip.id, listOf(seat), trip.boardingPoints[0].id, trip.droppingPoints[0].id,
                listOf(PassengerInput("Rahim", Gender.MALE, 30, seat)), "01712345678", null, trip.price + map.serviceChargePerSeat, "k"),
        )
        assertEquals("promo_invalid", code { bookings.applyPromo(held.id, "NOPE") })
        assertEquals("promo_wrong_mode", code { bookings.applyPromo(held.id, "FLY8") })
        val withCode = bookings.applyPromo(held.id, " jatri150 ")
        assertEquals("JATRI150", withCode.promoCode)
        assertEquals(Money.taka(150), withCode.discount)
        assertEquals(held.total - Money.taka(150), withCode.total)
        val removed = bookings.removePromo(held.id)
        assertNull(removed.promoCode)
        assertEquals(held.total, removed.total)

        val again = bookings.applyPromo(held.id, "JATRI150")
        val start = payments.start(held.id, PaymentMethod.BKASH, "p")
        assertEquals(again.total, start.amount)
        payments.completeDemo(start.paymentId, success = true)
        val paid = bookings.get(held.id)
        assertEquals(BookingStatus.TICKETED, paid.status)
        assertEquals("JATRI150", paid.promoCode)
        assertEquals("promo_not_allowed", code { bookings.removePromo(held.id) })

        // RAIL50 needs Nagad.
        val trains = NetworkTrainRepository(api, locations)
        val t = trains.search(locations.getById("stn_dhaka")!!, locations.getById("stn_chattogram")!!, today.plusDays(3), null)
            .first { x -> x.classes.any { it.trainClass == TrainClass.SNIGDHA } }
        val tm = trains.seatMap(t.id, TrainClass.SNIGDHA)
        val ts = tm.coaches.flatMap { it.layout.rows.flatten().filterNotNull() }.first { it.state == SeatState.AVAILABLE }.number
        val th = trains.hold(
            TrainHoldRequest(t.id, TrainClass.SNIGDHA, listOf(ts), listOf(PassengerInput("Rahim", Gender.MALE, 34, ts, "1234567890")),
                "01712345678", null, tm.fare + tm.serviceChargePerSeat, "t"),
        )
        assertEquals(Money.taka(50), bookings.applyPromo(th.id, "RAIL50").discount)
        assertEquals("promo_method", code { payments.start(th.id, PaymentMethod.BKASH, "q") })
        assertNotNull(payments.start(th.id, PaymentMethod.NAGAD, "r").paymentId)
    }

    @Test
    fun `saved travellers and price alerts`() = runTest {
        DefaultAuthRepository(api, Store(), backgroundScope).verifyOtp("01712345678", MockJatriApi.MOCK_OTP)
        val travellers = NetworkTravellerRepository(api)
        val rahim = travellers.save(SavedTraveller(null, "Rahim Uddin", "Rahim", "Uddin", Gender.MALE, LocalDate.of(1990, 5, 1), nid = "1990123456789"))
        assertNotNull(rahim.id)
        val edited = travellers.save(rahim.copy(fullName = "Rahim U."))
        assertEquals(rahim.id, edited.id)
        assertEquals(listOf("Rahim U."), travellers.list().map { it.fullName })
        travellers.delete(rahim.id!!)
        assertTrue(travellers.list().isEmpty())

        val alerts = NetworkPriceAlertRepository(api)
        val a = alerts.create(TravelMode.BUS, "bus_dhaka", "bus_coxsbazar", today.plusDays(4), Money.taka(300))
        assertNotNull(a.lastPrice)
        assertTrue(a.lastPrice!! > a.target)
        assertEquals(listOf(a.id), alerts.list().map { it.id })
        alerts.delete(a.id)
        assertTrue(alerts.list().isEmpty())
    }
}
