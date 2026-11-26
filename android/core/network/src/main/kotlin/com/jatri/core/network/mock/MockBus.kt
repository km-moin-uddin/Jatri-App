package com.jatri.core.network.mock

import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.model.BookingCreateRequest
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.BusTripDto
import com.jatri.core.network.model.PassengerOutDto
import com.jatri.core.network.model.PaymentInitDto
import com.jatri.core.network.model.PaymentStatusDto
import com.jatri.core.network.model.TicketDto
import com.jatri.core.network.model.SeatDto
import com.jatri.core.network.model.SeatMapDto
import com.jatri.core.network.model.SegmentDto
import com.jatri.core.network.model.StopPointDto
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * In-app copy of the backend's MockBusProvider and booking-hold rules (same trip-id format,
 * pricing rules, seat rules and error codes; exact times and booked seats differ), used when the
 * APK runs without a server.
 */
/** Checkout URLs with this scheme are the in-app demo gateway (no browser). */
const val MOCK_CHECKOUT_SCHEME = "jatri-demo-checkout://"

internal class MockBus {
    private val bd = ZoneOffset.ofHours(6)
    private val operators = listOf("Sample Paribahan", "Demo Travels", "Test Express", "Night Coach Demo", "Mock Lines")
    private val hours = mapOf(
        setOf("bus_dhaka", "bus_coxsbazar") to 10, setOf("bus_dhaka", "bus_chattogram") to 6,
        setOf("bus_dhaka", "bus_sylhet") to 6, setOf("bus_dhaka", "bus_rajshahi") to 6,
        setOf("bus_dhaka", "bus_khulna") to 7, setOf("bus_dhaka", "bus_barishal") to 5,
        setOf("bus_dhaka", "bus_rangpur") to 7, setOf("bus_chattogram", "bus_coxsbazar") to 4,
    )
    private val points = mapOf(
        "bus_dhaka" to listOf("Gabtoli" to "গাবতলী", "Kalabagan" to "কলাবাগান", "Sayedabad" to "সায়েদাবাদ", "Arambagh" to "আরামবাগ"),
        "bus_chattogram" to listOf("Dampara" to "দামপাড়া", "Oxygen Mor" to "অক্সিজেন মোড়"),
        "bus_coxsbazar" to listOf("Kolatoli" to "কলাতলী", "Cox's Bazar Bus Terminal" to "কক্সবাজার বাস টার্মিনাল"),
        "bus_sylhet" to listOf("Kadamtoli" to "কদমতলী", "Subhanighat" to "সোবহানীঘাট"),
    )

    private val held = mutableMapOf<String, String>() // "trip|seat" -> bookingId
    private val bookings = mutableMapOf<String, BookingDto>()
    private val idempotency = mutableMapOf<String, String>()

    private fun rng(vararg parts: String): Random {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return Random(digest.take(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) })
    }

    private fun tripId(from: String, to: String, on: LocalDate, i: Int) = "mb~$from~$to~$on~$i"

    private data class Built(val trip: BusTripDto, val rows: List<List<SeatDto?>>, val layout: String)

    private fun build(from: String, to: String, on: LocalDate, index: Int): Built {
        val r = rng(from, to, on.toString(), index.toString())
        val h = hours[setOf(from, to)] ?: (4 + r.nextInt(7))
        val depart = OffsetDateTime.of(on, LocalTime.of(listOf(7, 9, 13, 21, 23)[index % 5], listOf(0, 15, 30, 45)[r.nextInt(4)]), bd)
        val arrive = depart.plusHours(h.toLong()).plusMinutes(listOf(0L, 30L)[r.nextInt(2)])
        val coach = listOf("ac", "non_ac", "ac", "sleeper", "non_ac")[index % 5]
        val perHour = mapOf("ac" to 140, "non_ac" to 90, "sleeper" to 220).getValue(coach)
        val fareTaka = maxOf(300, Math.round(h * perHour / 50.0).toInt() * 50)
        val (layout, rows) = seats(from, to, on, index, coach)
        val available = rows.flatten().filterNotNull().count { it.state != "booked" }
        val op = operators[index % operators.size]
        val trip = BusTripDto(
            id = tripId(from, to, on, index), operatorName = op,
            coachNumber = "${op.take(2).uppercase()}-%02d".format(1 + r.nextInt(99)), coachType = coach,
            fromId = from, toId = to, departAt = depart.toString(), arriveAt = arrive.toString(),
            fare = fareTaka * 100L, seatsAvailable = available,
            boardingPoints = stops(from, depart, listOf(-30, -15, 0, 10)), droppingPoints = stops(to, arrive, listOf(0, 15, 30)),
        )
        return Built(trip, rows, layout)
    }

    private fun stops(location: String, base: OffsetDateTime, offsets: List<Int>): List<StopPointDto> {
        val names = points[location] ?: listOf("Bus Terminal" to "বাস টার্মিনাল", "Counter" to "কাউন্টার")
        return names.mapIndexed { i, (en, bn) -> StopPointDto("$location:$i", en, bn, base.plusMinutes(offsets[i % offsets.size].toLong()).toString()) }
    }

    private fun seats(from: String, to: String, on: LocalDate, index: Int, coach: String): Pair<String, List<List<SeatDto?>>> {
        val r = rng("seats", from, to, on.toString(), index.toString())
        val sleeper = coach == "sleeper"
        val letters = "ABCDEFGHIJ".take(if (sleeper) 8 else 10)
        val (left, right) = if (sleeper) 1 to 2 else 2 to 2
        val bookedRatio = 0.25 + r.nextDouble() * 0.5
        val rows = letters.mapIndexed { row, letter ->
            fun seat(n: Int): SeatDto {
                val num = "$letter$n"
                val state = when {
                    row == 3 && !sleeper && n <= 2 -> "ladies"
                    r.nextDouble() < bookedRatio -> "booked"
                    else -> "available"
                }
                return SeatDto(num, state)
            }
            (1..left).map(::seat) + listOf(null) + (left + 1..left + right).map(::seat)
        }
        return (if (sleeper) "2+1" else "2+2") to rows
    }

    private fun parse(tripId: String): Built? {
        val p = tripId.split("~")
        if (p.size != 5 || p[0] != "mb") return null
        return runCatching { build(p[1], p[2], LocalDate.parse(p[3]), p[4].toInt()) }.getOrNull()
    }

    fun search(from: String, to: String, date: String, coach: String?): List<BusTripDto> {
        if (from == to || !from.startsWith("bus_") || !to.startsWith("bus_")) return emptyList()
        val on = LocalDate.parse(date)
        val now = OffsetDateTime.now()
        return (0 until 5).map { build(from, to, on, it).trip }
            .filter { OffsetDateTime.parse(it.departAt).isAfter(now) }
            .filter { coach == null || it.coachType == coach }
    }

    fun seatMap(tripId: String): SeatMapDto {
        val b = parse(tripId) ?: throw tripNotFound()
        expireHolds()
        val rows = b.rows.map { row ->
            row.map { s -> if (s != null && s.state != "booked" && held.containsKey("$tripId|${s.number}")) s.copy(state = "held") else s }
        }
        return SeatMapDto(b.trip, b.layout, rows, maxSelectable = 4, serviceChargePerSeat = 3000)
    }

    fun create(body: BookingCreateRequest, key: String, phone: String): BookingDto {
        idempotency["$phone|$key"]?.let { return bookings.getValue(it) }
        val b = parse(body.tripId) ?: throw tripNotFound()
        expireHolds()
        val states = b.rows.flatten().filterNotNull().associate { it.number to it.state }
        if (body.seats.any { it !in states }) throw api("invalid_seats", "Please choose valid seats.", "সঠিক সিট বেছে নিন।")
        if (body.seats.any { states[it] == "booked" || held.containsKey("${body.tripId}|$it") }) {
            throw api("seat_unavailable", "Some seats were just taken. Please choose other seats.", "কিছু সিট এইমাত্র অন্য কেউ নিয়েছে। অন্য সিট বেছে নিন।")
        }
        if (body.passengers.any { states[it.seatNo] == "ladies" && it.gender != "female" }) {
            throw api("ladies_seat", "Seats reserved for ladies can only be booked for female passengers.", "মহিলা সিট শুধু নারী যাত্রীর জন্য বুক করা যাবে।")
        }
        val boarding = b.trip.boardingPoints.firstOrNull { it.id == body.boardingPointId }
        val dropping = b.trip.droppingPoints.firstOrNull { it.id == body.droppingPointId }
        if (boarding == null || dropping == null) throw api("invalid_point", "Please choose a boarding and a dropping point.", "ওঠার ও নামার স্থান বেছে নিন।")
        val base = b.trip.fare * body.seats.size
        val service = 3000L * body.seats.size
        if (body.expectedTotal != null && body.expectedTotal != base + service) {
            val taka = "%,d".format((base + service) / 100)
            throw api("price_changed", "The fare has changed. The new total is ৳ $taka.", "ভাড়া পরিবর্তন হয়েছে। নতুন মোট ভাড়া ৳ $taka।")
        }
        val id = UUID.randomUUID().toString()
        body.seats.forEach { held["${body.tripId}|$it"] = id }
        val dto = BookingDto(
            id = id, bookingRef = "JT" + (1..4).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString(""),
            mode = "bus", status = "held", holdExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10).toString(),
            baseFare = base, serviceCharge = service, discount = 0, totalAmount = base + service, currency = "BDT",
            contactPhone = body.contactPhone, contactEmail = body.contactEmail,
            segments = listOf(
                SegmentDto(
                    body.tripId, b.trip.operatorName, b.trip.coachNumber, b.trip.fromId, b.trip.toId, b.trip.departAt, b.trip.arriveAt,
                    b.trip.coachType, body.seats.sorted(), boarding, dropping,
                ),
            ),
            passengers = body.passengers.map {
                PassengerOutDto(
                    fullName = it.fullName.trim(),
                    type = if ((it.age ?: 30) >= 12) "adult" else "child",
                    gender = it.gender,
                    age = it.age,
                    seatNo = it.seatNo,
                    documentMasked = it.documentNo?.let { d -> "•".repeat(d.length - 4) + d.takeLast(4) },
                )
            },
            createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
        )
        bookings[id] = dto
        idempotency["$phone|$key"] = id
        return dto
    }

    fun get(id: String): BookingDto {
        expireHolds()
        return bookings[id] ?: throw api("booking_not_found", "Booking not found.", "বুকিং পাওয়া যায়নি।")
    }

    fun list(): List<BookingDto> {
        expireHolds()
        return bookings.values.sortedByDescending { it.createdAt }
    }

    fun cancel(id: String): BookingDto {
        val b = get(id)
        if (b.status != "held") return b
        release(id)
        return b.copy(status = "cancelled").also { bookings[id] = it }
    }

    private val payments = mutableMapOf<String, PaymentStatusDto>()
    private val paymentAmounts = mutableMapOf<String, Long>()

    fun initPayment(bookingId: String): PaymentInitDto {
        val b = get(bookingId)
        if (b.status != "held") {
            throw api("booking_not_payable", "This booking can no longer be paid. Please choose seats again.", "এই বুকিংয়ের পেমেন্ট আর করা যাবে না। আবার সিট বেছে নিন।")
        }
        val id = UUID.randomUUID().toString()
        payments[id] = PaymentStatusDto(id, "initiated", null, b.id, b.status, b.bookingRef)
        paymentAmounts[id] = b.totalAmount
        return PaymentInitDto(id, "$MOCK_CHECKOUT_SCHEME$id", b.totalAmount, "initiated")
    }

    fun paymentStatus(id: String): PaymentStatusDto {
        val p = payments[id] ?: throw api("payment_not_found", "Payment not found.", "পেমেন্ট পাওয়া যায়নি।")
        return p.copy(bookingStatus = get(p.bookingId).status)
    }

    /** What the backend's verified callback does: confirm the hold and issue the ticket. */
    fun completePayment(id: String, success: Boolean) {
        val p = payments[id] ?: return
        if (p.status != "initiated") return
        if (!success) {
            payments[id] = p.copy(status = "failed", failureReason = "declined")
            return
        }
        val b = get(p.bookingId)
        val pnr = "PNR-" + id.take(8).uppercase()
        bookings[b.id] = b.copy(
            status = "ticketed",
            holdExpiresAt = null,
            passengers = b.passengers.mapIndexed { i, pass -> pass.copy(ticketNumber = "$pnr-${i + 1}") },
            ticket = TicketDto(qrPayload = "JT1.${b.bookingRef}.${b.id.replace("-", "")}.demo", pdfUrl = "", issuedAt = OffsetDateTime.now(ZoneOffset.UTC).toString()),
        )
        release(b.id)
        payments[id] = p.copy(status = "success", bookingStatus = "ticketed")
    }

    private fun expireHolds() {
        val now = OffsetDateTime.now()
        bookings.values.filter { it.status == "held" && it.holdExpiresAt != null && OffsetDateTime.parse(it.holdExpiresAt).isBefore(now) }.forEach {
            release(it.id)
            bookings[it.id] = it.copy(status = "cancelled")
        }
    }

    private fun release(bookingId: String) {
        held.entries.removeAll { it.value == bookingId }
    }

    private fun tripNotFound() = api("trip_not_found", "This trip is no longer available.", "এই ট্রিপটি আর পাওয়া যাচ্ছে না।")
    private fun api(code: String, en: String, bn: String) = AppException(AppError.Api(code, en, bn))
}
