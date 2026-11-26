package com.jatri.core.common.format

import com.jatri.core.model.AppLanguage
import com.jatri.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatterTest {
    @Test
    fun `formats small amounts without grouping`() {
        assertEquals("৳ 950", Money.taka(950).format(AppLanguage.ENGLISH))
    }

    @Test
    fun `formats thousands like the design spec`() {
        assertEquals("৳ 1,250", Money.taka(1250).format(AppLanguage.ENGLISH))
    }

    @Test
    fun `uses lakh grouping for large amounts`() {
        assertEquals("৳ 1,25,000", Money.taka(125_000).format(AppLanguage.ENGLISH))
        assertEquals("৳ 12,34,567", Money.taka(1_234_567).format(AppLanguage.ENGLISH))
    }

    @Test
    fun `uses Bangla digits in Bangla`() {
        assertEquals("৳ ১,২৫০", Money.taka(1250).format(AppLanguage.BANGLA))
    }

    @Test
    fun `shows paisa only when not zero`() {
        assertEquals("৳ 99.50", Money(9950).format(AppLanguage.ENGLISH))
        assertEquals("৳ 99.05", Money(9905).format(AppLanguage.ENGLISH))
    }

    @Test
    fun `handles negative amounts for discounts`() {
        assertEquals("-৳ 200", Money.taka(-200).format(AppLanguage.ENGLISH))
    }
}
