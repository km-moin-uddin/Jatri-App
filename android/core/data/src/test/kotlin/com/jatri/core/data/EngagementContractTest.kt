package com.jatri.core.data

import com.jatri.core.data.repository.toModel
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.DiscountType
import com.jatri.core.model.Gender
import com.jatri.core.model.Money
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.TravelMode
import com.jatri.core.network.di.NetworkModule
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.OfferDto
import com.jatri.core.network.model.PriceAlertDto
import com.jatri.core.network.model.TravellerDto
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Real FastAPI responses (Phase 8): offers, a booking with a promo, saved travellers, price alerts. */
class EngagementContractTest {
    private val json = NetworkModule.providesJson()
    private fun read(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()
    private fun <T> list(name: String, s: kotlinx.serialization.KSerializer<T>) =
        json.decodeFromString(ApiEnvelope.serializer(ListSerializer(s)), read(name)).data!!

    @Test
    fun `offers keep their rules and show flat values in taka`() {
        val offers = list("offers.json", OfferDto.serializer()).map { it.toModel() }.associateBy { it.code }
        assertTrue(offers.keys.containsAll(listOf("JATRI150", "FLY8", "RAIL50", "WELCOME100")))
        val fly = offers.getValue("FLY8")
        assertEquals(DiscountType.PERCENT, fly.discountType)
        assertEquals(8L, fly.value.toLong())
        assertEquals(Money.taka(800), fly.maxDiscount)
        assertEquals(setOf(PaymentMethod.BKASH), fly.methods)
        assertEquals(setOf(TravelMode.FLIGHT), fly.modes)
        val jatri = offers.getValue("JATRI150")
        assertEquals(DiscountType.FLAT, jatri.discountType)
        assertEquals(150L, jatri.value.toLong())
        assertTrue(offers.getValue("WELCOME100").firstBookingOnly)
    }

    @Test
    fun `promo booking shows the code and adds up`() {
        val b = json.decodeFromString(ApiEnvelope.serializer(BookingDto.serializer()), read("booking_promo.json")).data!!.toModel()
        assertEquals(BookingStatus.HELD, b.status)
        assertEquals("JATRI150", b.promoCode)
        assertEquals(Money.taka(150), b.discount)
        assertEquals(b.total, b.baseFare + b.taxes + b.serviceCharge - b.discount)
    }

    @Test
    fun `saved traveller has full documents for the owner`() {
        val t = list("travellers.json", TravellerDto.serializer()).single().toModel()
        assertEquals(Gender.MALE, t.gender)
        assertEquals("1990123456789", t.nid)
        assertEquals(LocalDate.of(1990, 5, 1), t.dateOfBirth)
        assertNotNull(t.id)
        assertNull(t.passportNo)
    }

    @Test
    fun `price alert maps target and last price`() {
        val a = list("price_alerts.json", PriceAlertDto.serializer()).single().toModel()
        assertEquals(TravelMode.BUS, a.mode)
        assertEquals(Money.taka(1200), a.target)
        assertEquals(Money.taka(900), a.lastPrice)
        assertTrue(a.isActive)
    }
}
