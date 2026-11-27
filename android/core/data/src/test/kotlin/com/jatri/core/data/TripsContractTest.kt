package com.jatri.core.data

import com.jatri.core.data.repository.toModel
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.RefundStatus
import com.jatri.core.network.di.NetworkModule
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.CancelQuoteDto
import com.jatri.core.network.model.NotificationPageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real FastAPI responses: cancel quote → cancelled & refunded bus booking → notifications inbox. */
class TripsContractTest {
    private val json = NetworkModule.providesJson()
    private fun read(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    fun `quote adds up and speaks Bangla digits`() {
        val q = json.decodeFromString(ApiEnvelope.serializer(CancelQuoteDto.serializer()), read("cancel_quote.json")).data!!
        assertTrue(q.cancellable)
        assertEquals(q.paid, q.refund + q.fee + q.nonRefundable)
        assertTrue(q.messageBn.contains("১০%"))
        assertNotNull(q.deadline)
    }

    @Test
    fun `refunded booking keeps refund and cancellation time`() {
        val b = json.decodeFromString(ApiEnvelope.serializer(BookingDto.serializer()), read("booking_refunded.json")).data!!.toModel()
        assertEquals(BookingStatus.REFUNDED, b.status)
        assertNotNull(b.cancelledAt)
        val r = b.refund!!
        assertEquals(RefundStatus.REFUNDED, r.status)
        assertEquals(b.total, r.amount + r.fee + b.serviceCharge)
    }

    @Test
    fun `notifications page maps booking links and unread count`() {
        val page = json.decodeFromString(ApiEnvelope.serializer(NotificationPageDto.serializer()), read("notifications.json")).data!!
        assertEquals(listOf("refund_processed", "booking_cancelled", "booking_confirmed"), page.items.map { it.type })
        assertEquals(3, page.unread)
        val n = page.items.first().let { com.jatri.core.data.repository.toModelForTest(it) }
        assertNotNull(n.bookingId)
        assertTrue(n.titleBn.isNotBlank() && n.bodyEn.contains("৳"))
    }
}
