package com.jatri.core.common.format

import com.jatri.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class BanglaDigitsTest {
    @Test
    fun `converts every digit`() {
        assertEquals("০১২৩৪৫৬৭৮৯", "0123456789".toBanglaDigits())
    }

    @Test
    fun `keeps other characters`() {
        assertEquals("DAC ১০:৩০", "DAC 10:30".toBanglaDigits())
    }

    @Test
    fun `formats durations in both languages`() {
        val d = Duration.ofMinutes(65)
        assertEquals("1h 05m", DateTimeFormats.duration(d, AppLanguage.ENGLISH))
        assertEquals("১ ঘ ০৫ মি", DateTimeFormats.duration(d, AppLanguage.BANGLA))
    }
}
