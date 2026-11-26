package com.jatri.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsCodeTest {
    @Test
    fun `reads the code from the backend SMS`() {
        assertEquals("482913", extractOtp("<#> Jatri code: 482913. Valid for 5 minutes. Do not share it.\nFA+9qCX9VSu"))
    }

    @Test
    fun `ignores longer numbers`() {
        assertNull(extractOtp("Call 01712345678 for help"))
    }
}
