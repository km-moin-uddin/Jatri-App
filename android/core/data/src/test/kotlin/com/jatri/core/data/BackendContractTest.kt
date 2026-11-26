package com.jatri.core.data

import com.jatri.core.data.repository.toModel
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.CoachType
import com.jatri.core.model.Gender
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.Money
import com.jatri.core.network.di.NetworkModule
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.BusTripDto
import com.jatri.core.network.model.SeatMapDto
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JSON files are real responses recorded from the FastAPI backend (search → seats → hold),
 * so a change on either side that breaks the contract fails here.
 */
class BackendContractTest {
    private val json = NetworkModule.providesJson()
    private fun read(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    private val dhaka = Location("bus_dhaka", LocationType.BUS_CITY, "dhaka", "Dhaka", "ঢাকা", "Dhaka", "ঢাকা")
    private val cox = Location("bus_coxsbazar", LocationType.BUS_CITY, "coxsbazar", "Cox's Bazar", "কক্সবাজার", "Cox's Bazar", "কক্সবাজার")

    @Test
    fun `search results map to trips in Bangladesh time`() {
        val env = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(BusTripDto.serializer())), read("search.json"))
        val trips = env.data!!.map { it.toModel(dhaka, cox) }
        assertTrue(trips.isNotEmpty())
        val t = trips.first()
        assertTrue(t.departAt.hour in 5..23) // +06:00 kept as local time, not shifted to UTC
        assertTrue(t.arriveAt.isAfter(t.departAt))
        assertEquals(0L, t.price.paisa % 100)
        assertTrue(t.coachType != CoachType.ANY)
        assertTrue(t.boardingPoints.isNotEmpty() && t.droppingPoints.isNotEmpty())
        assertTrue(t.boardingPoints.first().nameBn.isNotBlank())
    }

    @Test
    fun `seat map has an aisle and known seat states`() {
        val env = json.decodeFromString(ApiEnvelope.serializer(SeatMapDto.serializer()), read("seats.json"))
        val dto = env.data!!
        assertTrue(dto.rows.all { row -> row.any { it == null } })
        assertTrue(dto.rows.flatten().filterNotNull().all { it.state in setOf("available", "booked", "ladies", "held") })
        assertEquals(4, dto.maxSelectable)
    }

    @Test
    fun `held booking maps with money, points, passengers and masked NID`() {
        val env = json.decodeFromString(ApiEnvelope.serializer(BookingDto.serializer()), read("booking.json"))
        val b = env.data!!.toModel()
        assertEquals(BookingStatus.HELD, b.status)
        assertNotNull(b.holdExpiresAt)
        assertTrue(b.ref.startsWith("JT"))
        assertEquals(b.baseFare + b.serviceCharge - b.discount, b.total)
        assertEquals(Money.taka(60), b.serviceCharge) // ৳30 × 2 seats
        val seg = b.segments.single()
        assertEquals(2, seg.seatNumbers.size)
        assertNotNull(seg.boardingPoint)
        assertEquals("রহিম উদ্দিন", b.passengers[0].fullName)
        assertEquals(Gender.FEMALE, b.passengers[1].gender)
        assertEquals("••••••7890", b.passengers[0].documentMasked)
    }
}
