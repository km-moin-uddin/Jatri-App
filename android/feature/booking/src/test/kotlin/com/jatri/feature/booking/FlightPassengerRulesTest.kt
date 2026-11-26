package com.jatri.feature.booking

import com.jatri.core.model.PassengerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FlightPassengerRulesTest {
    private val trip = LocalDate.of(2026, 12, 10)

    @Test
    fun `typed dates are DDMMYYYY`() {
        assertEquals(LocalDate.of(1990, 8, 15), parseDigitsDate("15081990"))
        assertNull(parseDigitsDate("31021990"))
        assertNull(parseDigitsDate("1508199"))
    }

    @Test
    fun `airline age bands use the travel dates`() {
        assertTrue(ageFits(PassengerType.ADULT, LocalDate.of(2014, 12, 10), trip, trip))
        assertFalse(ageFits(PassengerType.ADULT, LocalDate.of(2014, 12, 11), trip, trip))
        assertTrue(ageFits(PassengerType.CHILD, LocalDate.of(2020, 1, 1), trip, trip))
        // Turns 12 before the return flight: no longer a child fare.
        assertFalse(ageFits(PassengerType.CHILD, LocalDate.of(2014, 12, 20), trip, trip.plusDays(14)))
        assertTrue(ageFits(PassengerType.INFANT, LocalDate.of(2025, 6, 1), trip, trip))
        assertFalse(ageFits(PassengerType.INFANT, LocalDate.of(2024, 12, 1), trip, trip))
    }

    @Test
    fun `date mask shows slashes`() {
        val out = DateDigitsTransformation.filter(androidx.compose.ui.text.AnnotatedString("15081990"))
        assertEquals("15/08/1990", out.text.text)
        assertEquals(10, out.offsetMapping.originalToTransformed(8))
        assertEquals("15/0", DateDigitsTransformation.filter(androidx.compose.ui.text.AnnotatedString("150")).text.text)
    }
}
