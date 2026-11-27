package com.jatri.core.network

import com.jatri.core.network.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CertificatePinsTest {
    @Test
    fun `no pins means no pinning`() {
        assertNull(NetworkModule.certificatePinner("", "https://api.example.com/api/v1/"))
        assertNull(NetworkModule.certificatePinner(" , ", "https://api.example.com/api/v1/"))
    }

    @Test
    fun `pins apply to the API host`() {
        val a = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val b = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
        val pinner = NetworkModule.certificatePinner("$a, $b", "https://api.example.com/api/v1/")!!
        assertEquals(setOf("api.example.com"), pinner.pins.map { it.pattern }.toSet())
        assertEquals(2, pinner.pins.size)
    }
}
