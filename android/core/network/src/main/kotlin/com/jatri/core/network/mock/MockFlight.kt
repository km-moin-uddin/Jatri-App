package com.jatri.core.network.mock

import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.FareRulesDto
import com.jatri.core.network.model.FlightBookingRequest
import com.jatri.core.network.model.FlightLegDto
import com.jatri.core.network.model.FlightOfferDto
import com.jatri.core.network.model.FlightSearchRequest
import com.jatri.core.network.model.FlightSliceDto
import com.jatri.core.network.model.PassengerOutDto
import com.jatri.core.network.model.PaxFareDto
import com.jatri.core.network.model.SegmentDto
import com.jatri.core.network.model.SliceDto
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * In-app copy of the backend's MockFlightProvider and flight booking rules (same offer-id format,
 * fare brands, passenger and passport rules, error codes; exact times and prices differ), used when
 * the APK runs without a server.
 */
internal class MockFlight(private val bookings: MockBus) {
    private val bd = ZoneOffset.ofHours(6)
    private val zones = mapOf(
        "CCU" to ZoneOffset.ofHoursMinutes(5, 30), "DXB" to ZoneOffset.ofHours(4), "KUL" to ZoneOffset.ofHours(8),
        "SIN" to ZoneOffset.ofHours(8), "BKK" to ZoneOffset.ofHours(7), "JED" to ZoneOffset.ofHours(3), "DOH" to ZoneOffset.ofHours(3),
    )
    private val airlines = listOf("S1" to "Sample Air", "D2" to "Demo Airways", "T3" to "Test Wings", "M4" to "Mock Air")
    private val domestic = setOf("DAC", "CGP", "CXB", "ZYL", "JSR", "SPD", "RJH", "BZL")
    private val west = setOf("DXB", "DOH", "JED")
    private val minutes = mapOf(
        setOf("DAC", "CXB") to 65, setOf("DAC", "CGP") to 50, setOf("DAC", "ZYL") to 45, setOf("DAC", "JSR") to 40,
        setOf("DAC", "SPD") to 60, setOf("DAC", "RJH") to 45, setOf("DAC", "BZL") to 35, setOf("CGP", "CXB") to 35,
        setOf("CGP", "ZYL") to 50, setOf("DAC", "CCU") to 60, setOf("DAC", "DXB") to 330, setOf("DAC", "KUL") to 240,
        setOf("DAC", "SIN") to 260, setOf("DAC", "BKK") to 150, setOf("DAC", "JED") to 420, setOf("DAC", "DOH") to 330,
        setOf("CGP", "DXB") to 340, setOf("CGP", "KUL") to 220, setOf("CGP", "JED") to 430, setOf("ZYL", "DXB") to 350,
    )
    private val cabinFactor = mapOf("economy" to 1.0, "premium_economy" to 1.6, "business" to 2.8)
    private val options = 6
    private val serviceCharge = 20_000L

    private fun rng(vararg parts: String): Random {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return Random(digest.take(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) })
    }

    private fun code(id: String) = id.removePrefix("air_")

    private fun minutes(a: String, b: String): Int = minutes[setOf(a, b)] ?: run {
        val r = rng("minutes", minOf(a, b), maxOf(a, b))
        if (a in domestic && b in domestic) 40 + r.nextInt(7) * 5 else 150 + r.nextInt(51) * 5
    }

    private fun hasDirect(a: String, b: String) = when {
        setOf(a, b) in minutes -> true
        a in domestic && b in domestic -> "DAC" == a || "DAC" == b
        else -> a !in domestic && b !in domestic
    }

    private fun local(code: String, utc: OffsetDateTime) = utc.withOffsetSameInstant(zones[code] ?: bd)

    // ------------------------------------------------------------------ building offers

    private data class Query(val slices: List<SliceDto>, val adults: Int, val children: Int, val infants: Int, val cabin: String)

    private val compactDate = DateTimeFormatter.BASIC_ISO_DATE

    private fun offerId(q: Query, option: Int, brand: String): String {
        val parts = q.slices.joinToString("_") { "${code(it.fromId)}.${code(it.toId)}.${LocalDate.parse(it.date).format(compactDate)}" }
        return "mf~$parts~${q.adults}.${q.children}.${q.infants}~${q.cabin}~$option~$brand"
    }

    private fun parse(id: String): Triple<Query, Int, String>? = runCatching {
        val p = id.split("~")
        require(p.size == 6 && p[0] == "mf" && p[3] in cabinFactor && p[5] in setOf("saver", "flex", "business"))
        val slices = p[1].split("_").map { chunk ->
            val (a, b, d) = chunk.split(".")
            SliceDto("air_$a", "air_$b", LocalDate.parse(d, compactDate).toString())
        }
        val (ad, ch, inf) = p[2].split(".").map { it.toInt() }
        Triple(Query(slices, ad, ch, inf, p[3]), p[4].toInt(), p[5])
    }.getOrNull()

    private class Leg(val dto: FlightLegDto, val depUtc: OffsetDateTime, val arrUtc: OffsetDateTime)

    private fun option(s: SliceDto, index: Int): List<Leg> {
        val a = code(s.fromId)
        val b = code(s.toId)
        val r = rng("opt", a, b, s.date, index.toString())
        val isDomestic = a in domestic && b in domestic
        val hours = if (isDomestic) listOf(7, 9, 11, 14, 17, 20) else listOf(1, 8, 10, 14, 19, 23)
        val dep = OffsetDateTime.of(LocalDate.parse(s.date), LocalTime.of(hours[index % hours.size], listOf(0, 10, 25, 40, 55)[r.nextInt(5)]), bd)
        val (carrier, name) = airlines[(index + a.length + b.length) % airlines.size]

        fun leg(x: String, y: String, at: OffsetDateTime): Leg {
            val m = minutes(x, y)
            val arr = at.plusMinutes(m.toLong())
            val aircraft = if (m < 80) "ATR 72-600" else if (m < 300) "Boeing 737-800" else "Boeing 787-9"
            val dto = FlightLegDto(
                carrierCode = carrier, carrierName = name, flightNumber = "$carrier ${101 + r.nextInt(889)}", aircraft = aircraft,
                fromId = "air_$x", toId = "air_$y", fromCode = x, toCode = y,
                departAt = local(x, at).toString(), arriveAt = local(y, arr).toString(), durationMinutes = m,
            )
            return Leg(dto, at, arr)
        }

        val oneStop = !hasDirect(a, b) || (minutes(a, b) > 200 && index >= 4)
        if (!oneStop) return listOf(leg(a, b, dep))
        val hub = if ((a in domestic && a != "DAC") || (b in domestic && b != "DAC")) {
            "DAC"
        } else {
            val prefer = if (a in west || b in west) listOf("DOH", "DXB") else listOf("BKK", "KUL")
            (prefer.drop(index % 2) + listOf("DOH", "DXB", "BKK")).first { it != a && it != b }
        }
        val first = leg(a, hub, dep)
        return listOf(first, leg(hub, b, first.arrUtc.plusMinutes(60L + r.nextInt(9) * 15L)))
    }

    private fun rules(brand: String, intl: Boolean) = when (brand) {
        "saver" -> FareRulesDto(false, null, (if (intl) 6000L else 2500L) * 100, if (intl) 24 else 3, 20, 7, "forfeit")
        "flex" -> FareRulesDto(true, (if (intl) 5000L else 1500L) * 100, (if (intl) 3000L else 1000L) * 100, if (intl) 24 else 3, if (intl) 30 else 20, 7, "refund_minus_fee")
        else -> FareRulesDto(true, (if (intl) 2000L else 500L) * 100, 0, if (intl) 12 else 2, if (intl) 40 else 30, 10, "refund_minus_fee")
    }

    private fun build(q: Query, option: Int, brand: String): FlightOfferDto? {
        val journeys = q.slices.mapIndexed { j, s -> option(s, (option + j * 2) % options) }
        for (j in 1 until journeys.size) {
            if (journeys[j].first().depUtc.isBefore(journeys[j - 1].last().arrUtc.plusHours(2))) return null
        }
        val codes = journeys.flatten().flatMap { listOf(it.dto.fromCode, it.dto.toCode) }.toSet()
        val intl = !domestic.containsAll(codes)
        val id = offerId(q, option, brand)
        val seatsLeft = 2 + rng("seats", id).nextInt(8)
        if (seatsLeft < q.adults + q.children) return null

        var taka = 0.0
        journeys.forEach { legs ->
            val m = legs.sumOf { minutes(it.dto.fromCode, it.dto.toCode) }
            var per = if (intl) 9000.0 + m * 75 else 2600.0 + m * 50
            if (legs.first().depUtc.withOffsetSameInstant(bd).hour in setOf(7, 8, 9, 17, 19, 20)) per *= 1.12
            if (legs.size > 1) per *= 0.92
            per *= 1 + rng("p", legs.first().dto.flightNumber, legs.first().dto.departAt.take(10)).nextInt(9) / 100.0
            taka += per
        }
        taka *= cabinFactor.getValue(q.cabin) * (if (brand == "flex") 1.18 else 1.0)
        val base = (taka / 10).roundToLong() * 10 * 100
        val tax = (((if (intl) 2500.0 else 600.0) * q.slices.size + base / 100.0 * (if (intl) 0.12 else 0.08)) / 10).roundToLong() * 1000
        val fares = buildList {
            add(PaxFareDto("adult", q.adults, base, tax))
            if (q.children > 0) add(PaxFareDto("child", q.children, (base * 0.75).toLong() / 1000 * 1000, tax))
            if (q.infants > 0) add(PaxFareDto("infant", q.infants, (base * 0.10).toLong() / 1000 * 1000, 0))
        }
        val baseTotal = fares.sumOf { it.base * it.count }
        val taxTotal = fares.sumOf { it.tax * it.count }
        val service = serviceCharge * (q.adults + q.children)
        return FlightOfferDto(
            id = id,
            slices = journeys.mapIndexed { j, legs ->
                FlightSliceDto(
                    fromId = q.slices[j].fromId, toId = q.slices[j].toId,
                    departAt = legs.first().dto.departAt, arriveAt = legs.last().dto.arriveAt,
                    durationMinutes = java.time.Duration.between(legs.first().depUtc, legs.last().arrUtc).toMinutes().toInt(),
                    stops = legs.size - 1, legs = legs.map { it.dto },
                )
            },
            cabin = q.cabin, fareBrand = brand, rules = rules(brand, intl), fares = fares,
            baseTotal = baseTotal, taxTotal = taxTotal, serviceCharge = service, total = baseTotal + taxTotal + service,
            seatsLeft = seatsLeft, international = intl,
        )
    }

    // ------------------------------------------------------------------ API

    fun search(body: FlightSearchRequest): List<FlightOfferDto> {
        if (body.infants > body.adults || body.adults + body.children > 9) {
            throw api("invalid_request", "Each infant needs an adult, and at most 9 passengers can book.", "প্রতি ইনফ্যান্টের সাথে একজন প্রাপ্তবয়স্ক লাগবে, সর্বোচ্চ ৯ জন।")
        }
        if (body.slices.isEmpty() || body.slices.any { it.fromId == it.toId || !it.fromId.startsWith("air_") || !it.toId.startsWith("air_") }) return emptyList()
        val q = Query(body.slices, body.adults, body.children, body.infants, body.cabin)
        val brands = if (body.cabin == "business") listOf("business") else listOf("saver", "flex")
        val cutoff = OffsetDateTime.now().plusHours(2)
        return (0 until options).flatMap { o -> brands.mapNotNull { build(q, o, it) } }
            .filter { OffsetDateTime.parse(it.slices.first().departAt).isAfter(cutoff) }
    }

    fun offer(id: String): FlightOfferDto {
        val (q, o, brand) = parse(id) ?: throw expired()
        val offer = build(q, o, brand) ?: throw expired()
        if (!OffsetDateTime.parse(offer.slices.first().departAt).isAfter(OffsetDateTime.now().plusHours(2))) throw expired()
        return offer
    }

    fun create(body: FlightBookingRequest, key: String, phone: String): BookingDto {
        bookings.existing(phone, key)?.let { return it }
        val offer = offer(body.offerId)
        checkPassengers(offer, body)
        if (body.expectedTotal != null && body.expectedTotal != offer.total) {
            val taka = "%,d".format(offer.total / 100)
            throw api("price_changed", "The fare has changed. The new total is ৳ $taka.", "ভাড়া পরিবর্তন হয়েছে। নতুন মোট ভাড়া ৳ $taka।")
        }
        val firstDay = LocalDate.parse(offer.slices.first().departAt.take(10))
        val dto = BookingDto(
            id = UUID.randomUUID().toString(),
            bookingRef = "JT" + (1..4).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString(""),
            mode = "flight", status = "held", holdExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10).toString(),
            baseFare = offer.baseTotal, taxes = offer.taxTotal, serviceCharge = offer.serviceCharge, discount = 0, totalAmount = offer.total,
            currency = "BDT", contactPhone = body.contactPhone, contactEmail = body.contactEmail,
            segments = offer.slices.flatMapIndexed { j, s ->
                s.legs.map { l ->
                    SegmentDto(
                        offer.id, l.carrierName, l.flightNumber, l.fromId, l.toId, l.departAt, l.arriveAt, offer.cabin, emptyList(), null, null,
                        sliceIndex = j, fromCode = l.fromCode, toCode = l.toCode, aircraft = l.aircraft, baggage = "${offer.rules.checkedBaggageKg} kg",
                    )
                }
            },
            passengers = body.passengers.map {
                PassengerOutDto(
                    fullName = "${it.givenName} ${it.surname}".uppercase().split(" ").filter(String::isNotBlank).joinToString(" "),
                    type = it.type, gender = it.gender,
                    age = Period.between(LocalDate.parse(it.dateOfBirth), firstDay).years,
                    documentMasked = it.documentNo?.let { d -> "•".repeat(d.length - 4) + d.takeLast(4) },
                    dateOfBirth = it.dateOfBirth, nationality = it.nationality,
                )
            },
            createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
            cabin = offer.cabin, fareBrand = offer.fareBrand, fareRules = offer.rules,
        )
        return bookings.register(dto, phone, key)
    }

    private fun checkPassengers(offer: FlightOfferDto, body: FlightBookingRequest) {
        val wanted = offer.fares.associate { it.type to it.count }
        val got = body.passengers.groupingBy { it.type }.eachCount()
        if (got != wanted) {
            throw api("passengers_mismatch", "The passengers don't match the number of adults, children and infants you searched for.",
                "যাত্রীর তথ্য খোঁজার সময়ের প্রাপ্তবয়স্ক, শিশু ও ইনফ্যান্টের সংখ্যার সাথে মিলছে না।")
        }
        val first = LocalDate.parse(offer.slices.first().departAt.take(10))
        val last = LocalDate.parse(offer.slices.last().departAt.take(10))
        val name = Regex("^[A-Za-z][A-Za-z .'-]*$")
        body.passengers.forEachIndexed { i, p ->
            val n = i + 1
            if (!name.matches(p.givenName) || !name.matches(p.surname)) {
                throw api("invalid_name", "Passenger $n: write the name in English letters, as on the passport/NID.", "যাত্রী $n: পাসপোর্ট/এনআইডির মতো ইংরেজি অক্ষরে নাম লিখুন।")
            }
            val dob = LocalDate.parse(p.dateOfBirth)
            val ageFirst = Period.between(dob, first).years
            val ageLast = Period.between(dob, last).years
            val ok = !dob.isAfter(LocalDate.now()) && when (p.type) {
                "adult" -> ageFirst >= 12
                "child" -> ageFirst >= 2 && ageLast < 12
                else -> ageLast < 2
            }
            if (!ok) {
                throw api("passenger_age", "Passenger $n: the date of birth doesn't match the passenger type (adult 12+, child 2-11, infant under 2).",
                    "যাত্রী $n: জন্মতারিখ যাত্রীর ধরনের সাথে মিলছে না (প্রাপ্তবয়স্ক ১২+, শিশু ২-১১, ইনফ্যান্ট ২-এর কম)।")
            }
            if (offer.international) {
                if (p.documentNo == null || !Regex("^[A-Z0-9]{6,9}$").matches(p.documentNo) || p.documentExpiry == null) {
                    throw api("passport_required", "Passenger $n: international flights need a passport number and its expiry date.",
                        "যাত্রী $n: আন্তর্জাতিক ফ্লাইটে পাসপোর্ট নম্বর ও মেয়াদ শেষের তারিখ লাগবে।")
                }
                if (LocalDate.parse(p.documentExpiry).isBefore(last.plusMonths(6))) {
                    throw api("passport_expiry", "Passenger $n: the passport must be valid for at least 6 months after the trip.",
                        "যাত্রী $n: যাত্রার পর অন্তত ৬ মাস পাসপোর্টের মেয়াদ থাকতে হবে।")
                }
            }
        }
    }

    private fun expired() = api("offer_expired", "This fare is no longer available. Please search again.", "এই ভাড়াটি আর পাওয়া যাচ্ছে না। আবার খুঁজুন।")
    private fun api(code: String, en: String, bn: String) = AppException(AppError.Api(code, en, bn))
}
