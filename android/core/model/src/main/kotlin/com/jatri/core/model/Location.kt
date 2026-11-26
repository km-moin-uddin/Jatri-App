package com.jatri.core.model

enum class LocationType { AIRPORT, BUS_CITY, STATION }

/**
 * An airport, bus city or train station.
 *
 * @param code IATA code for airports (e.g. `DAC`), a short slug for cities and stations.
 */
data class Location(
    val id: String,
    val type: LocationType,
    val code: String,
    val nameEn: String,
    val nameBn: String,
    val cityEn: String,
    val cityBn: String,
    val country: String = "BD",
    val isPopular: Boolean = false,
) {
    fun name(language: AppLanguage): String = if (language == AppLanguage.BANGLA) nameBn else nameEn
    fun city(language: AppLanguage): String = if (language == AppLanguage.BANGLA) cityBn else cityEn
}

fun TravelMode.locationType(): LocationType = when (this) {
    TravelMode.FLIGHT -> LocationType.AIRPORT
    TravelMode.BUS -> LocationType.BUS_CITY
    TravelMode.TRAIN -> LocationType.STATION
}
