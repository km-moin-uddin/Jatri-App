package com.jatri.core.data.repository

import com.jatri.core.data.seed.SeedLocations
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import kotlinx.coroutines.flow.flowOf

class SeedLocationRepositoryForTest : LocationRepository {
    override fun observeLocations(type: LocationType) = flowOf(SeedLocations.all.filter { it.type == type })
    override suspend fun getById(id: String): Location? = SeedLocations.byId[id]
}
