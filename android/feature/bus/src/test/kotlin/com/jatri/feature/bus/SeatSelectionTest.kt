package com.jatri.feature.bus

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jatri.core.data.repository.LocationRepository
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric because type-safe navigation args (toRoute) go through android.os.Bundle. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SeatSelectionTest {
    private object NoLocations : LocationRepository {
        override fun observeLocations(type: LocationType): Flow<List<Location>> = emptyFlow()
        override suspend fun getById(id: String): Location? = null
    }

    private lateinit var vm: BusSeatsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val handle = SavedStateHandle(
            mapOf("tripId" to "b1", "fromId" to "x", "toId" to "y", "date" to "2026-12-01"),
        )
        vm = BusSeatsViewModel(handle, NoLocations)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `selects and deselects a seat`() {
        val seat = Seat("F1", SeatState.AVAILABLE)
        vm.toggle(seat)
        assertEquals(setOf("F1"), vm.state.value.selected)
        vm.toggle(seat)
        assertEquals(emptySet<String>(), vm.state.value.selected)
    }

    @Test
    fun `ignores booked seats`() {
        vm.toggle(Seat("A1", SeatState.BOOKED))
        assertEquals(emptySet<String>(), vm.state.value.selected)
    }

    @Test
    fun `allows at most four seats`() {
        listOf("F1", "F2", "F3", "F4", "J1").forEach { vm.toggle(Seat(it, SeatState.AVAILABLE)) }
        assertEquals(setOf("F1", "F2", "F3", "F4"), vm.state.value.selected)
    }
}
