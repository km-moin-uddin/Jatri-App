package com.jatri.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jatri.core.data.repository.DefaultAuthRepository
import com.jatri.core.data.repository.NetworkBookingRepository
import com.jatri.core.data.repository.NetworkPaymentRepository
import com.jatri.core.data.repository.NetworkTrainRepository
import com.jatri.core.data.repository.SeedLocationRepositoryForTest
import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.datastore.session.StoredSession
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Gender
import com.jatri.core.model.PassengerInput
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.SeatState
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TrainHoldRequest
import com.jatri.core.network.mock.MockJatriApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** The demo APK's train flow: search → coaches → hold (NID rules) → pay → ticketed, seats sold. */
@RunWith(AndroidJUnit4::class)
class DemoTrainFlowTest {
    private class Store : SessionStore {
        val state = MutableStateFlow<StoredSession?>(null)
        override val session = state
        override suspend fun current() = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private val api = MockJatriApi()
    private val locations = SeedLocationRepositoryForTest()
    private val trains = NetworkTrainRepository(api, locations)
    private val bookings = NetworkBookingRepository(api)
    private val payments = NetworkPaymentRepository(api, ApplicationProvider.getApplicationContext())
    private val today = LocalDate.now(ZoneId.of("Asia/Dhaka"))

    private suspend fun code(block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (e: AppException) {
        (e.error as AppError.Api).code
    }

    @Test
    fun `book a train end to end and respect the railway's rules`() = runTest {
        DefaultAuthRepository(api, Store(), backgroundScope).verifyOtp("01712345678", MockJatriApi.MOCK_OTP)
        val dhaka = locations.getById("stn_dhaka")!!
        val ctg = locations.getById("stn_chattogram")!!

        assertEquals("outside_sale_window", code { trains.search(dhaka, ctg, today.plusDays(11), null) })
        val day = today.plusDays(3)
        val trips = trains.search(dhaka, ctg, day, null)
        assertTrue(trips.isNotEmpty())
        assertTrue(trips.none { it.offDay == day.dayOfWeek })

        val trip = trips.first { t -> t.classes.any { it.trainClass == TrainClass.SNIGDHA } }
        val map = trains.seatMap(trip.id, TrainClass.SNIGDHA)
        val free = map.coaches.flatMap { it.layout.rows.flatten().filterNotNull() }.filter { it.state == SeatState.AVAILABLE }.map { it.number }
        assertEquals(trip.classes.first { it.trainClass == TrainClass.SNIGDHA }.seatsAvailable, free.size)
        val seats = free.take(2)
        fun request(nid: String?, key: String) = TrainHoldRequest(
            trip.id, TrainClass.SNIGDHA, seats,
            listOf(PassengerInput("Rahim", Gender.MALE, 34, seats[0], nid), PassengerInput("Mim", Gender.FEMALE, 9, seats[1])),
            "01712345678", null, (map.fare + map.serviceChargePerSeat) * 2, key,
        )
        assertEquals("nid_required", code { trains.hold(request(null, "a")) })
        val held = trains.hold(request("1234567890", "b"))
        assertEquals(BookingStatus.HELD, held.status)
        assertEquals(seats.sortedWith(compareBy({ it.substringBefore('-') }, { it.substringAfter('-').toInt() })), held.segments[0].seatNumbers)
        // While held, nobody else can pick these seats.
        val again = trains.seatMap(trip.id, TrainClass.SNIGDHA).coaches.flatMap { it.layout.rows.flatten().filterNotNull() }
        assertTrue(again.filter { it.number in seats }.all { it.state == SeatState.HELD })

        val start = payments.start(held.id, PaymentMethod.BKASH, "p")
        payments.completeDemo(start.paymentId, success = true)
        assertEquals(BookingStatus.TICKETED, bookings.get(held.id).status)
        val after = trains.seatMap(trip.id, TrainClass.SNIGDHA).coaches.flatMap { it.layout.rows.flatten().filterNotNull() }
        assertTrue(after.filter { it.number in seats }.all { it.state == SeatState.BOOKED })
    }
}
