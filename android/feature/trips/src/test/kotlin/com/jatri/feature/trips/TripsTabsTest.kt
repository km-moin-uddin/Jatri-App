package com.jatri.feature.trips

import com.jatri.core.model.Booking
import com.jatri.core.model.BookingSegment
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Money
import com.jatri.core.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class TripsTabsTest {
    private val now = LocalDateTime.of(2026, 11, 27, 9, 0)

    private fun booking(id: String, status: BookingStatus, depart: LocalDateTime) = Booking(
        id = id, ref = "JT$id", mode = TravelMode.BUS, status = status, holdExpiresAt = null,
        baseFare = Money.taka(100), serviceCharge = Money.ZERO, discount = Money.ZERO, total = Money.taka(100),
        contactPhone = "01712345678", contactEmail = null,
        segments = listOf(BookingSegment("Op", "1", "a", "b", depart, depart.plusHours(5), "ac", listOf("A1"), null, null)),
        passengers = emptyList(),
    )

    @Test
    fun `bookings land in the right tab`() {
        assertEquals(TripsTab.UPCOMING, tabOf(booking("1", BookingStatus.TICKETED, now.plusDays(1)), now))
        assertEquals(TripsTab.COMPLETED, tabOf(booking("2", BookingStatus.TICKETED, now.minusHours(1)), now))
        assertEquals(TripsTab.UPCOMING, tabOf(booking("3", BookingStatus.HELD, now.plusDays(1)), now))
        assertNull(tabOf(booking("4", BookingStatus.HELD, now.minusDays(1)), now))
        assertEquals(TripsTab.CANCELLED, tabOf(booking("5", BookingStatus.REFUNDED, now.plusDays(1)), now))
        assertEquals(TripsTab.CANCELLED, tabOf(booking("6", BookingStatus.FAILED, now.plusDays(1)), now))
    }

    @Test
    fun `upcoming soonest first, others latest first`() {
        val state = TripsUiState(
            loading = false,
            now = now,
            bookings = listOf(
                booking("a", BookingStatus.TICKETED, now.plusDays(3)),
                booking("b", BookingStatus.TICKETED, now.plusDays(1)),
                booking("c", BookingStatus.TICKETED, now.minusDays(5)),
                booking("d", BookingStatus.TICKETED, now.minusDays(1)),
            ),
        )
        assertEquals(listOf("b", "a"), state.list(TripsTab.UPCOMING).map { it.id })
        assertEquals(listOf("d", "c"), state.list(TripsTab.COMPLETED).map { it.id })
    }
}
