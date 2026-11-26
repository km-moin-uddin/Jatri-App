package com.jatri.core.data

import com.jatri.core.data.seed.MockContent
import com.jatri.core.data.seed.SeedLocations
import com.jatri.core.model.LocationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {
    @Test
    fun `location ids are unique`() {
        val ids = SeedLocations.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every location has a Bangla and an English name`() {
        SeedLocations.all.forEach {
            assertTrue(it.id, it.nameEn.isNotBlank() && it.nameBn.isNotBlank() && it.cityBn.isNotBlank())
        }
    }

    @Test
    fun `domestic airports from the spec are present`() {
        val codes = SeedLocations.airports.map { it.code }.toSet()
        listOf("DAC", "CXB", "CGP", "ZYL", "JSR", "SPD", "RJH", "BZL").forEach { assertTrue(it, it in codes) }
    }

    @Test
    fun `popular routes connect two different places of the right type`() {
        MockContent.popularRoutes.forEach {
            assertTrue(it.from.id != it.to.id)
            assertEquals(it.from.type, it.to.type)
        }
    }

    @Test
    fun `mock seat layout is 2 plus 2 with an aisle`() {
        val layout = MockContent.busSeatLayout()
        assertEquals(10, layout.rows.size)
        layout.rows.forEach { row ->
            assertEquals(5, row.size)
            assertEquals(null, row[2])
        }
        assertTrue(SeedLocations.busCities.all { it.type == LocationType.BUS_CITY })
    }
}
