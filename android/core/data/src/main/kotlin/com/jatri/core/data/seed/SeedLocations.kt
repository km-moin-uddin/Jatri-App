package com.jatri.core.data.seed

import com.jatri.core.model.Location
import com.jatri.core.model.LocationType

/**
 * Bundled seed list of airports, bus cities and train stations. The backend `/locations` endpoint
 * becomes the source of truth from Phase 2; this list stays as the offline fallback.
 */
internal object SeedLocations {
    private fun airport(code: String, en: String, bn: String, cityEn: String, cityBn: String, country: String = "BD", popular: Boolean = false) =
        Location("air_$code", LocationType.AIRPORT, code, en, bn, cityEn, cityBn, country, popular)

    private fun city(code: String, en: String, bn: String, popular: Boolean = false) =
        Location("bus_$code", LocationType.BUS_CITY, code, en, bn, en, bn, "BD", popular)

    private fun station(code: String, en: String, bn: String, cityEn: String, cityBn: String, popular: Boolean = false) =
        Location("stn_$code", LocationType.STATION, code, en, bn, cityEn, cityBn, "BD", popular)

    val airports = listOf(
        airport("DAC", "Hazrat Shahjalal International Airport", "হযরত শাহজালাল আন্তর্জাতিক বিমানবন্দর", "Dhaka", "ঢাকা", popular = true),
        airport("CGP", "Shah Amanat International Airport", "শাহ আমানত আন্তর্জাতিক বিমানবন্দর", "Chattogram", "চট্টগ্রাম", popular = true),
        airport("CXB", "Cox's Bazar Airport", "কক্সবাজার বিমানবন্দর", "Cox's Bazar", "কক্সবাজার", popular = true),
        airport("ZYL", "Osmani International Airport", "ওসমানী আন্তর্জাতিক বিমানবন্দর", "Sylhet", "সিলেট", popular = true),
        airport("JSR", "Jashore Airport", "যশোর বিমানবন্দর", "Jashore", "যশোর"),
        airport("SPD", "Saidpur Airport", "সৈয়দপুর বিমানবন্দর", "Saidpur", "সৈয়দপুর"),
        airport("RJH", "Shah Makhdum Airport", "শাহ মখদুম বিমানবন্দর", "Rajshahi", "রাজশাহী"),
        airport("BZL", "Barishal Airport", "বরিশাল বিমানবন্দর", "Barishal", "বরিশাল"),
        airport("CCU", "Netaji Subhas Chandra Bose International Airport", "নেতাজি সুভাষচন্দ্র বসু আন্তর্জাতিক বিমানবন্দর", "Kolkata", "কলকাতা", "IN", popular = true),
        airport("DXB", "Dubai International Airport", "দুবাই আন্তর্জাতিক বিমানবন্দর", "Dubai", "দুবাই", "AE", popular = true),
        airport("KUL", "Kuala Lumpur International Airport", "কুয়ালালামপুর আন্তর্জাতিক বিমানবন্দর", "Kuala Lumpur", "কুয়ালালামপুর", "MY", popular = true),
        airport("SIN", "Singapore Changi Airport", "সিঙ্গাপুর চাঙ্গি বিমানবন্দর", "Singapore", "সিঙ্গাপুর", "SG"),
        airport("BKK", "Suvarnabhumi Airport", "সুবর্ণভূমি বিমানবন্দর", "Bangkok", "ব্যাংকক", "TH"),
        airport("JED", "King Abdulaziz International Airport", "বাদশাহ আবদুল আজিজ আন্তর্জাতিক বিমানবন্দর", "Jeddah", "জেদ্দা", "SA"),
        airport("DOH", "Hamad International Airport", "হামাদ আন্তর্জাতিক বিমানবন্দর", "Doha", "দোহা", "QA"),
    )

    val busCities = listOf(
        city("dhaka", "Dhaka", "ঢাকা", popular = true),
        city("chattogram", "Chattogram", "চট্টগ্রাম", popular = true),
        city("coxsbazar", "Cox's Bazar", "কক্সবাজার", popular = true),
        city("sylhet", "Sylhet", "সিলেট", popular = true),
        city("rajshahi", "Rajshahi", "রাজশাহী", popular = true),
        city("khulna", "Khulna", "খুলনা", popular = true),
        city("barishal", "Barishal", "বরিশাল"),
        city("rangpur", "Rangpur", "রংপুর"),
        city("mymensingh", "Mymensingh", "ময়মনসিংহ"),
        city("cumilla", "Cumilla", "কুমিল্লা"),
        city("bogura", "Bogura", "বগুড়া"),
        city("jashore", "Jashore", "যশোর"),
        city("dinajpur", "Dinajpur", "দিনাজপুর"),
        city("kushtia", "Kushtia", "কুষ্টিয়া"),
        city("feni", "Feni", "ফেনী"),
        city("teknaf", "Teknaf", "টেকনাফ"),
        city("bandarban", "Bandarban", "বান্দরবান"),
        city("rangamati", "Rangamati", "রাঙ্গামাটি"),
        city("sreemangal", "Sreemangal", "শ্রীমঙ্গল"),
        city("kuakata", "Kuakata", "কুয়াকাটা"),
    )

    val stations = listOf(
        station("dhaka", "Dhaka (Kamalapur)", "ঢাকা (কমলাপুর)", "Dhaka", "ঢাকা", popular = true),
        station("dhaka_airport", "Dhaka Airport", "ঢাকা বিমানবন্দর", "Dhaka", "ঢাকা", popular = true),
        station("chattogram", "Chattogram", "চট্টগ্রাম", "Chattogram", "চট্টগ্রাম", popular = true),
        station("coxsbazar", "Cox's Bazar", "কক্সবাজার", "Cox's Bazar", "কক্সবাজার", popular = true),
        station("sylhet", "Sylhet", "সিলেট", "Sylhet", "সিলেট", popular = true),
        station("rajshahi", "Rajshahi", "রাজশাহী", "Rajshahi", "রাজশাহী", popular = true),
        station("khulna", "Khulna", "খুলনা", "Khulna", "খুলনা"),
        station("rangpur", "Rangpur", "রংপুর", "Rangpur", "রংপুর"),
        station("mymensingh", "Mymensingh", "ময়মনসিংহ", "Mymensingh", "ময়মনসিংহ"),
        station("cumilla", "Cumilla", "কুমিল্লা", "Cumilla", "কুমিল্লা"),
        station("dinajpur", "Dinajpur", "দিনাজপুর", "Dinajpur", "দিনাজপুর"),
        station("jashore", "Jashore", "যশোর", "Jashore", "যশোর"),
        station("joydebpur", "Joydebpur", "জয়দেবপুর", "Gazipur", "গাজীপুর"),
        station("bhairab", "Bhairab Bazar", "ভৈরব বাজার", "Bhairab", "ভৈরব"),
        station("akhaura", "Akhaura", "আখাউড়া", "Akhaura", "আখাউড়া"),
        station("sreemangal", "Sreemangal", "শ্রীমঙ্গল", "Sreemangal", "শ্রীমঙ্গল"),
        station("bogura", "Bogura", "বগুড়া", "Bogura", "বগুড়া"),
        station("panchagarh", "Panchagarh", "পঞ্চগড়", "Panchagarh", "পঞ্চগড়"),
        station("benapole", "Benapole", "বেনাপোল", "Benapole", "বেনাপোল"),
    )

    val all: List<Location> = airports + busCities + stations
    val byId: Map<String, Location> = all.associateBy { it.id }
}
