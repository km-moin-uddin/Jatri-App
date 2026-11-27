package com.jatri.core.network.mock

import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.CoachDto
import com.jatri.core.network.model.PassengerOutDto
import com.jatri.core.network.model.SeatDto
import com.jatri.core.network.model.SegmentDto
import com.jatri.core.network.model.TrainBookingRequest
import com.jatri.core.network.model.TrainClassDto
import com.jatri.core.network.model.TrainSeatMapDto
import com.jatri.core.network.model.TrainTripDto
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * In-app copy of the backend's MockTrainProvider and train booking rules (same trip-id format,
 * classes, coach names, 10-day sale window, off days, NID rule and error codes; exact seats differ),
 * used when the APK runs without a server.
 */
internal class MockTrain(private val bookings: MockBus) {
    private val bd = ZoneOffset.ofHours(6)
    private val kmFromDhaka = mapOf(
        "stn_dhaka" to 0, "stn_dhaka_airport" to 15, "stn_joydebpur" to 30, "stn_bhairab" to 80, "stn_mymensingh" to 120,
        "stn_akhaura" to 130, "stn_cumilla" to 160, "stn_sreemangal" to 230, "stn_bogura" to 250, "stn_chattogram" to 320,
        "stn_sylhet" to 320, "stn_jashore" to 330, "stn_rajshahi" to 345, "stn_benapole" to 360, "stn_khulna" to 400,
        "stn_rangpur" to 400, "stn_dinajpur" to 410, "stn_coxsbazar" to 470, "stn_panchagarh" to 520,
    )
    private val rate = mapOf(
        "shovan" to 0.89, "shovan_chair" to 1.08, "snigdha" to 2.05, "ac_seat" to 2.46, "f_seat" to 1.64, "f_berth" to 2.62, "ac_berth" to 3.68,
    )
    private val coachNames = mapOf(
        "shovan" to listOf("DA", "DHA"), "shovan_chair" to listOf("GA", "GHA", "UMA"), "snigdha" to listOf("CHA", "JA"),
        "ac_seat" to listOf("SCHA"), "f_seat" to listOf("NEO"), "f_berth" to listOf("TA"), "ac_berth" to listOf("KA", "KHA"),
    )
    private val trains = listOf(
        "Sample Express" to listOf("shovan_chair", "snigdha", "ac_berth"),
        "Demo Intercity" to listOf("shovan", "shovan_chair", "ac_seat"),
        "Test Mail" to listOf("shovan", "shovan_chair", "f_seat"),
        "Mock Nishitha" to listOf("shovan_chair", "snigdha", "f_berth", "ac_berth"),
    )
    private val departHours = listOf(6, 9, 15, 22)
    private val offDays = listOf("tuesday", null, "friday", null)
    private val days = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val saleDays = 10L

    private fun rng(vararg parts: String): Random {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return Random(digest.take(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) })
    }

    private fun km(a: String, b: String): Int {
        val ka = kmFromDhaka[a] ?: return 200
        val kb = kmFromDhaka[b] ?: return 200
        if (ka == 0 || kb == 0 || a in setOf("stn_dhaka_airport", "stn_joydebpur") || b in setOf("stn_dhaka_airport", "stn_joydebpur")) {
            return maxOf(15, kotlin.math.abs(ka - kb))
        }
        return maxOf(60, ((ka + kb) * 0.55).toInt())
    }

    private fun tripId(from: String, to: String, on: LocalDate, i: Int) = "mt~$from~$to~$on~$i"

    private fun parse(id: String): Triple<Pair<String, String>, LocalDate, Int>? = runCatching {
        val p = id.split("~")
        require(p.size == 5 && p[0] == "mt")
        val i = p[4].toInt()
        require(i in trains.indices)
        Triple(p[1] to p[2], LocalDate.parse(p[3]), i)
    }.getOrNull()

    private fun sold(tripId: String): Set<String> =
        bookings.list().filter { it.mode == "train" && it.status == "ticketed" && it.segments.firstOrNull()?.tripId == tripId }
            .flatMap { it.segments.first().seatNumbers }.toSet()

    private fun coaches(from: String, to: String, on: LocalDate, index: Int, klass: String): List<CoachDto> {
        val r = rng("coach", from, to, on.toString(), index.toString(), klass)
        val berth = klass.endsWith("berth")
        val bookedRatio = 0.3 + r.nextDouble() * 0.65
        val id = tripId(from, to, on, index)
        val soldSeats = sold(id)
        return coachNames.getValue(klass).take(1 + r.nextInt(2)).map { name ->
            var n = 1
            val rows = (0 until if (berth) 8 else 15).map {
                (0 until 4).flatMap { col ->
                    val number = "$name-$n"
                    n++
                    val state = when {
                        number in soldSeats || r.nextDouble() < bookedRatio -> "booked"
                        bookings.isHeld(id, number) -> "held"
                        else -> "available"
                    }
                    if (col == 2) listOf(null, SeatDto(number, state)) else listOf(SeatDto(number, state))
                }
            }
            CoachDto(name, if (berth) "berth" else "2+2", rows)
        }
    }

    private fun build(from: String, to: String, on: LocalDate, index: Int): TrainTripDto {
        val r = rng("train", from, to, on.toString(), index.toString())
        val distance = km(from, to)
        val (name, classes) = trains[index]
        val depart = OffsetDateTime.of(on, LocalTime.of(departHours[index], listOf(0, 15, 30, 45)[r.nextInt(4)]), bd)
        val speed = if (index == 2) 36.0 else 48.0
        val arrive = depart.plusMinutes((distance / speed * 60).toLong() + listOf(0L, 10L, 20L)[r.nextInt(3)])
        val base = MessageDigest.getInstance("SHA-256").digest("${minOf(from, to)}${maxOf(from, to)}$index".toByteArray())
            .take(2).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
        val number = 701 + (base % 90) * 2 + if (from < to) 0 else 1
        return TrainTripDto(
            id = tripId(from, to, on, index), trainName = name, trainNumber = number.toString(), fromId = from, toId = to,
            departAt = depart.toString(), arriveAt = arrive.toString(), offDay = offDays[index],
            classes = classes.map { c ->
                val seats = coaches(from, to, on, index, c).sumOf { coach -> coach.rows.flatten().count { it?.state == "available" } }
                TrainClassDto(c, maxOf(50L, (distance * rate.getValue(c) / 5).roundToLong() * 5) * 100, seats)
            },
        )
    }

    private fun checkSaleWindow(on: LocalDate) {
        val today = OffsetDateTime.now(bd).toLocalDate()
        if (on.isBefore(today) || on.isAfter(today.plusDays(saleDays))) {
            throw api(
                "outside_sale_window",
                "Train tickets go on sale $saleDays days before the journey. Choose a date within the next $saleDays days.",
                "যাত্রার ১০ দিন আগে থেকে ট্রেনের টিকিট বিক্রি হয়। আগামী ১০ দিনের মধ্যে একটি তারিখ বেছে নিন।",
            )
        }
    }

    private fun runs(trip: TrainTripDto, on: LocalDate) = trip.offDay != days[on.dayOfWeek.value - 1]

    fun search(from: String, to: String, date: String, klass: String?): List<TrainTripDto> {
        val on = LocalDate.parse(date)
        checkSaleWindow(on)
        if (from == to || !from.startsWith("stn_") || !to.startsWith("stn_")) return emptyList()
        val now = OffsetDateTime.now()
        return trains.indices.map { build(from, to, on, it) }
            .filter { runs(it, on) && OffsetDateTime.parse(it.departAt).isAfter(now) }
            .filter { t -> klass == null || t.classes.any { it.travelClass == klass } }
    }

    private fun trip(id: String): Pair<TrainTripDto, Triple<Pair<String, String>, LocalDate, Int>> {
        val p = parse(id) ?: throw tripNotFound()
        val t = build(p.first.first, p.first.second, p.second, p.third)
        if (!runs(t, p.second)) throw tripNotFound()
        return t to p
    }

    fun seatMap(tripId: String, klass: String): TrainSeatMapDto {
        val (t, p) = trip(tripId)
        val fare = t.classes.firstOrNull { it.travelClass == klass } ?: throw classNotFound()
        return TrainSeatMapDto(t, klass, fare.fare, coaches(p.first.first, p.first.second, p.second, p.third, klass), 4, 2000)
    }

    fun create(body: TrainBookingRequest, key: String, phone: String): BookingDto {
        bookings.existing(phone, key)?.let { return it }
        body.passengers.forEachIndexed { i, pass ->
            if (pass.age >= 18 && pass.documentNo.isNullOrBlank()) {
                throw api(
                    "nid_required",
                    "Passenger ${i + 1}: Bangladesh Railway needs the NID number of every passenger aged 18 or over.",
                    "যাত্রী ${i + 1}: ১৮ বছর বা তার বেশি বয়সী প্রত্যেক যাত্রীর এনআইডি নম্বর রেলওয়ের জন্য প্রয়োজন।",
                )
            }
        }
        val map = seatMap(body.tripId, body.travelClass)
        checkSaleWindow(OffsetDateTime.parse(map.trip.departAt).toLocalDate())
        val seats = map.coaches.flatMap { c -> c.rows.flatten().filterNotNull().map { it to c.name } }.associate { (s, c) -> s.number to (s.state to c) }
        if (body.seats.any { it !in seats }) throw api("invalid_seats", "Please choose valid seats.", "সঠিক সিট বেছে নিন।")
        if (body.seats.any { seats.getValue(it).first != "available" }) {
            throw api("seat_unavailable", "Some seats were just taken. Please choose other seats.", "কিছু সিট এইমাত্র অন্য কেউ নিয়েছে। অন্য সিট বেছে নিন।")
        }
        val base = map.fare * body.seats.size
        val service = map.serviceChargePerSeat * body.seats.size
        if (body.expectedTotal != null && body.expectedTotal != base + service) {
            val taka = "%,d".format((base + service) / 100)
            throw api("price_changed", "The fare has changed. The new total is ৳ $taka.", "ভাড়া পরিবর্তন হয়েছে। নতুন মোট ভাড়া ৳ $taka।")
        }
        val id = UUID.randomUUID().toString()
        bookings.lockSeats(body.tripId, body.seats, id)
        val t = map.trip
        val sorted = body.seats.sortedWith(compareBy({ it.substringBefore('-') }, { it.substringAfter('-').toIntOrNull() ?: 0 }))
        val dto = BookingDto(
            id = id, bookingRef = "JT" + (1..4).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString(""),
            mode = "train", status = "held", holdExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10).toString(),
            baseFare = base, serviceCharge = service, discount = 0, totalAmount = base + service, currency = "BDT",
            contactPhone = body.contactPhone, contactEmail = body.contactEmail,
            segments = listOf(
                SegmentDto(
                    body.tripId, t.trainName, t.trainNumber, t.fromId, t.toId, t.departAt, t.arriveAt, body.travelClass, sorted, null, null,
                    coach = body.seats.map { seats.getValue(it).second }.toSortedSet().joinToString(", "),
                ),
            ),
            passengers = body.passengers.map {
                PassengerOutDto(
                    fullName = it.fullName.trim(), type = if (it.age >= 12) "adult" else "child", gender = it.gender, age = it.age,
                    seatNo = it.seatNo, documentMasked = it.documentNo?.let { d -> "•".repeat(d.length - 4) + d.takeLast(4) },
                )
            },
            createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
        )
        return bookings.register(dto, phone, key)
    }

    private fun tripNotFound() = api("trip_not_found", "This trip is no longer available.", "এই ট্রিপটি আর পাওয়া যাচ্ছে না।")
    private fun classNotFound() = api("class_not_found", "This class is not available on this train.", "এই ট্রেনে এই শ্রেণির আসন নেই।")
    private fun api(code: String, en: String, bn: String) = AppException(AppError.Api(code, en, bn))
}
