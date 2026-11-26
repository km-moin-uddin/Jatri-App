package com.jatri.feature.flight

import com.jatri.core.data.seed.MockContent
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.Money
import com.jatri.core.ui.result.ResultSort
import com.jatri.feature.flight.navigation.FlightResultsDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FlightFiltersTest {
    private val day = LocalDate.of(2026, 12, 10)
    private val dac = Location("air_DAC", LocationType.AIRPORT, "DAC", "Shahjalal", "শাহজালাল", "Dhaka", "ঢাকা")
    private val cxb = Location("air_CXB", LocationType.AIRPORT, "CXB", "Cox's Bazar", "কক্সবাজার", "Cox's Bazar", "কক্সবাজার")
    private val offers = MockContent.flightOffers(day, dac, cxb)
    private val state = FlightResultsUiState(
        args = FlightResultsDestination("air_DAC,air_CXB,$day", 1, 0, 0, "ECONOMY"),
        loading = false,
        offers = offers,
    )

    @Test
    fun `no filters keeps everything, cheapest first`() {
        assertEquals(offers.size, state.visible.size)
        assertEquals(offers.minOf { it.price }, state.visible.first().price)
        assertEquals(0, state.filters.activeCount)
    }

    @Test
    fun `each filter narrows the list and is counted`() {
        val refundable = state.copy(filters = FlightFilters(refundableOnly = true))
        assertTrue(refundable.visible.all { it.rules.refundable })
        assertEquals(1, refundable.filters.activeCount)

        val morning = state.copy(filters = FlightFilters(slots = setOf(DepartSlot.MORNING)))
        assertTrue(morning.visible.all { it.departAt.hour in 6..11 })

        val cheap = state.copy(filters = FlightFilters(maxPrice = Money.taka(5500)))
        assertTrue(cheap.visible.all { it.price <= Money.taka(5500) })

        val airline = state.copy(filters = FlightFilters(airlines = setOf("Mock Air"), maxStops = 0))
        assertEquals(listOf("Mock Air"), airline.visible.map { it.airlineName })
        assertEquals(2, airline.filters.activeCount)
    }

    @Test
    fun `sorting and encoded journeys`() {
        val earliest = state.copy(sort = ResultSort.EARLIEST).visible
        assertEquals(earliest.sortedBy { it.departAt }, earliest)
        val args = FlightResultsDestination("air_DAC,air_DXB,2026-12-10;air_DXB,air_DAC,2026-12-17", 2, 1, 0, "ECONOMY", "ROUND_TRIP")
        assertEquals(2, args.searchLegs.size)
        assertEquals(LocalDate.of(2026, 12, 17), args.searchLegs[1].date)
        assertEquals(args, args.withLegs(args.searchLegs))
    }
}
