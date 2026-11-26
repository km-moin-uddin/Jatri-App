package com.jatri.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneValidationTest {
    @Test
    fun `accepts valid operator prefixes`() {
        listOf("01712345678", "01812345678", "01912345678", "01312345678", "01512345678").forEach {
            assertTrue(it, isValidBdPhone(it))
        }
    }

    @Test
    fun `rejects wrong length or prefix`() {
        listOf("0171234567", "017123456789", "01212345678", "02712345678", "").forEach {
            assertFalse(it, isValidBdPhone(it))
        }
    }

    @Test
    fun `converts Bangla digits typed on a Bangla keyboard`() {
        assertEquals("01712345678", "০১৭১২৩৪৫৬৭৮".toAsciiDigits())
    }
}
