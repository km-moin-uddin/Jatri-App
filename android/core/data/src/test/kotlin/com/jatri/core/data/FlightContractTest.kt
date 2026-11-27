package com.jatri.core.data

import com.jatri.core.data.repository.SeedLocationRepositoryForTest
import com.jatri.core.data.repository.toModel
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.CabinClass
import com.jatri.core.model.FareBrand
import com.jatri.core.model.PassengerType
import com.jatri.core.model.TravelMode
import com.jatri.core.network.di.NetworkModule
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.FlightOfferDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real FastAPI responses for a Dhaka ⇄ Dubai round trip (1 adult + 1 child): search → offer → hold → ticketed. */
class FlightContractTest {
    private val json = NetworkModule.providesJson()
    private val locations = SeedLocationRepositoryForTest()
    private fun read(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    fun `search offers keep each airport's local time and add up`() = runTest {
        val env = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(FlightOfferDto.serializer())), read("flight_search.json"))
        val offers = env.data!!.map { it.toModel(locations) }
        assertTrue(offers.isNotEmpty())
        val o = offers.first()
        assertEquals(2, o.journeys.size)
        assertTrue(o.international)
        assertEquals("DAC", o.journeys[0].from.code)
        assertEquals("Dhaka", o.journeys[0].from.cityEn) // resolved from the seed list
        val outbound = o.journeys[0]
        // DAC 01:10 (+06:00) → DXB 04:40 (+04:00): wall clocks differ by 3.5 h, the flight is 5.5 h.
        assertEquals(330L, outbound.duration.toMinutes())
        assertEquals(1, outbound.departAt.hour)
        assertEquals(4, outbound.arriveAt.hour)
        assertEquals(o.baseTotal + o.taxTotal + o.serviceCharge, o.price)
        assertEquals(1, o.count(PassengerType.ADULT))
        assertEquals(1, o.count(PassengerType.CHILD))
        assertEquals(CabinClass.ECONOMY, o.cabinClass)
        assertTrue(o.fareBrand in FareBrand.entries)
        if (!o.rules.refundable) assertEquals(null, o.rules.refundFee)
    }

    @Test
    fun `offer details match the search`() = runTest {
        val offer = json.decodeFromString(ApiEnvelope.serializer(FlightOfferDto.serializer()), read("flight_offer.json")).data!!.toModel(locations)
        val first = json.decodeFromString(ApiEnvelope.serializer(ListSerializer(FlightOfferDto.serializer())), read("flight_search.json"))
            .data!!.first().toModel(locations)
        assertEquals(first, offer)
    }

    @Test
    fun `held and ticketed flight bookings map journeys, taxes, rules and e-tickets`() {
        val held = json.decodeFromString(ApiEnvelope.serializer(BookingDto.serializer()), read("flight_booking.json")).data!!.toModel()
        assertEquals(TravelMode.FLIGHT, held.mode)
        assertEquals(BookingStatus.HELD, held.status)
        assertEquals(2, held.journeys.size)
        assertEquals(held.baseFare + held.taxes + held.serviceCharge, held.total)
        assertNotNull(held.fareRules)
        assertEquals(CabinClass.ECONOMY, held.cabinClass)
        assertEquals(listOf(PassengerType.ADULT, PassengerType.CHILD), held.passengers.map { it.type })
        assertEquals(4, held.segments[0].arriveAt.hour) // Dubai local time
        assertEquals("20 kg", held.segments[0].baggage)

        val done = json.decodeFromString(ApiEnvelope.serializer(BookingDto.serializer()), read("flight_booking_ticketed.json")).data!!.toModel()
        assertEquals(BookingStatus.TICKETED, done.status)
        assertTrue(done.passengers.all { it.ticketNumber!!.matches(Regex("^000-\\d{10}$")) })
        assertTrue(done.ticket!!.qrPayload.startsWith("JT1.${done.ref}."))
    }
}
