package com.jatri.feature.bus

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jatri.core.data.repository.BusRepository
import com.jatri.core.data.seed.MockContent
import com.jatri.core.model.BusSeatMap
import com.jatri.core.model.CoachType
import com.jatri.core.model.Location
import com.jatri.core.model.Money
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatState
import com.jatri.core.model.StopPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/** Robolectric because type-safe navigation args (toRoute) go through android.os.Bundle. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SeatSelectionTest {
    private val route = MockContent.popularRoutes.first()
    private val point = StopPoint("p1", "Gabtoli", "গাবতলী", LocalDateTime.of(2026, 12, 1, 7, 30))
    private val trip = MockContent.busTrips(LocalDate.of(2026, 12, 1), route.from, route.to).first()
        .copy(boardingPoints = listOf(point), droppingPoints = listOf(point.copy(id = "p2")))

    private inner class FakeBuses : BusRepository {
        override suspend fun search(from: Location, to: Location, date: LocalDate, coachType: CoachType) = listOf(trip)
        override suspend fun seatMap(tripId: String) = BusSeatMap(trip, MockContent.busSeatLayout(), 4, Money.taka(30))
    }

    private lateinit var vm: BusSeatsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = BusSeatsViewModel(SavedStateHandle(mapOf("tripId" to trip.id)), FakeBuses())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `selects and deselects a seat and prices it with the service charge`() {
        val seat = Seat("F1", SeatState.AVAILABLE)
        vm.toggle(seat)
        assertEquals(setOf("F1"), vm.state.value.selected)
        assertEquals(trip.price + Money.taka(30), vm.state.value.total)
        vm.toggle(seat)
        assertEquals(emptySet<String>(), vm.state.value.selected)
    }

    @Test
    fun `ignores booked and held seats`() {
        vm.toggle(Seat("A1", SeatState.BOOKED))
        vm.toggle(Seat("A3", SeatState.HELD))
        assertEquals(emptySet<String>(), vm.state.value.selected)
    }

    @Test
    fun `allows at most four seats and flags the limit`() {
        listOf("F1", "F2", "F3", "F4", "J1").forEach { vm.toggle(Seat(it, SeatState.AVAILABLE)) }
        assertEquals(setOf("F1", "F2", "F3", "F4"), vm.state.value.selected)
        assertTrue(vm.state.value.limitHit)
    }

    @Test
    fun `continue needs seats and picks default boarding and dropping points`() {
        assertNull(vm.selection())
        vm.toggle(Seat("F1", SeatState.AVAILABLE))
        val sel = vm.selection()!!
        assertEquals(listOf("F1"), sel.seats)
        assertEquals("p1", sel.boardingPointId)
        assertEquals("p2", sel.droppingPointId)
    }
}
