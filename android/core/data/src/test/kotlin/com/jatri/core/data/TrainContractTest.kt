package com.jatri.core.data

import com.jatri.core.data.repository.toModel
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TravelMode
import com.jatri.core.network.di.NetworkModule
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.TrainSeatMapDto
import com.jatri.core.network.model.TrainTripDto
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real FastAPI responses for Dhaka → Sylhet trains: search → Snigdha coaches → hold → ticketed. */
class TrainContractTest {
    private val json = NetworkModule.providesJson()
    private fun read(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()
    private val dhaka = Location("stn_dhaka", LocationType.STATION, "dhaka", "Dhaka (Kamalapur)", "ঢাকা (কমলাপুর)", "Dhaka", "ঢাকা")
    private val sylhet = Location("stn_sylhet", LocationType.STATION, "sylhet", "Sylhet", "সিলেট", "Sylhet", "সিলেট")

    @Test
    fun `search maps classes, fares and off days`() {
        val trips = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(TrainTripDto.serializer())), read("train_search.json"))
            .data!!.map { it.toModel(dhaka, sylhet) }
        assertTrue(trips.isNotEmpty())
        assertTrue(trips.all { it.classes.isNotEmpty() && it.arriveAt.isAfter(it.departAt) })
        assertTrue(trips.flatMap { it.classes }.all { it.price.paisa % 500 == 0L })
        val chair = trips.flatMap { it.classes }.first { it.trainClass == TrainClass.SHOVAN_CHAIR }
        assertEquals(345_00L, chair.price.paisa) // Dhaka–Sylhet Shovan Chair, like the railway's fare
    }

    @Test
    fun `coach seat map has named coaches and aisles`() {
        val dto = json.decodeFromString(ApiEnvelope.serializer(TrainSeatMapDto.serializer()), read("train_seats.json")).data!!
        assertEquals("snigdha", dto.travelClass)
        assertTrue(dto.coaches.isNotEmpty())
        assertTrue(dto.coaches.all { c -> c.rows.all { row -> row.count { it == null } == 1 } })
        assertTrue(dto.coaches.all { c -> c.rows.flatten().filterNotNull().all { it.number.startsWith(c.name + "-") } })
    }

    @Test
    fun `held and ticketed train bookings`() {
        val held = json.decodeFromString(ApiEnvelope.serializer(BookingDto.serializer()), read("train_booking.json")).data!!.toModel()
        assertEquals(TravelMode.TRAIN, held.mode)
        assertEquals(BookingStatus.HELD, held.status)
        assertEquals("snigdha", held.segments[0].travelClass)
        assertEquals("CHA", held.segments[0].coach)
        assertEquals(listOf("CHA-1", "CHA-9"), held.segments[0].seatNumbers)
        assertEquals("রহিম উদ্দিন", held.passengers[0].fullName)
        val done = json.decodeFromString(ApiEnvelope.serializer(BookingDto.serializer()), read("train_booking_ticketed.json")).data!!.toModel()
        assertEquals(BookingStatus.TICKETED, done.status)
        assertTrue(done.passengers.all { it.ticketNumber != null })
    }
}
