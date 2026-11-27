package com.jatri.core.network.mock

import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.model.BookingCreateRequest
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.CancelQuoteDto
import com.jatri.core.network.model.NotificationDto
import com.jatri.core.network.model.OfferDto
import com.jatri.core.network.model.RefundDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    /** Seat locks shared with the mock train provider ("trip|seat" -> booking id). */
    fun isHeld(tripId: String, seat: String): Boolean {
        expireHolds()
        return held.containsKey("$tripId|$seat")
    }

    fun lockSeats(tripId: String, seats: List<String>, bookingId: String) = seats.forEach { held["$tripId|$it"] = bookingId }

    /** Shared booking store: the mock flight and train providers keep their holds here too. */
    fun existing(phone: String, key: String): BookingDto? = idempotency["$phone|$key"]?.let { bookings[it] }

    fun register(dto: BookingDto, phone: String, key: String): BookingDto {
        bookings[dto.id] = dto
        idempotency["$phone|$key"] = dto.id
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

    fun cancel(id: String, expectedRefund: Long? = null): BookingDto {
        val b = get(id)
        if (b.status == "held") {
            release(id)
            return b.copy(status = "cancelled").also { bookings[id] = it }
        }
        if (b.status == "cancelled" || b.status == "refunded") return b
        val q = quote(id)
        if (!q.cancellable) throw api(q.reason ?: "not_cancellable", q.messageEn, q.messageBn)
        if (expectedRefund != null && expectedRefund != q.refund) {
            val taka = "%,d".format(q.refund / 100)
            throw api("refund_changed", "The refund has changed. You would now get ৳ $taka.", "রিফান্ডের পরিমাণ বদলেছে। এখন আপনি পাবেন ৳ ${bn(taka)}।")
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC).toString()
        // The demo gateway gives the money back at once, like the backend's mock gateway.
        val refund = if (q.refund > 0) RefundDto(q.refund, q.fee, "refunded", now) else null
        val updated = b.copy(status = if (refund != null) "refunded" else "cancelled", cancelledAt = now, refund = refund)
        bookings[id] = updated
        val tailEn = if (q.refund > 0) " ৳ ${"%,d".format(q.refund / 100)} will be refunded to your payment account." else " No refund applies to this fare."
        val tailBn = if (q.refund > 0) " ৳ ${bn("%,d".format(q.refund / 100))} আপনার পেমেন্ট অ্যাকাউন্টে ফেরত যাবে।" else " এই ভাড়ায় কোনো রিফান্ড নেই।"
        notify("booking_cancelled", "Booking cancelled", "Booking ${b.bookingRef} is cancelled.$tailEn", "বুকিং বাতিল হয়েছে", "বুকিং ${b.bookingRef} বাতিল হয়েছে।$tailBn", id)
        if (refund != null) {
            notify(
                "refund_processed", "Refund sent", "৳ ${"%,d".format(q.refund / 100)} for booking ${b.bookingRef} has been refunded.",
                "রিফান্ড পাঠানো হয়েছে", "বুকিং ${b.bookingRef}-এর ৳ ${bn("%,d".format(q.refund / 100))} ফেরত দেওয়া হয়েছে।", id,
            )
        }
        return updated
    }

    /** Same policies as the backend: bus 24h+/12h+, train 48h+/24h+/12h+, flights by fare rules. */
    fun quote(id: String): CancelQuoteDto {
        val b = get(id)
        val first = b.segments.minOf { OffsetDateTime.parse(it.departAt) }
        val hours = java.time.Duration.between(OffsetDateTime.now(), first).toMinutes() / 60.0
        fun no(reason: String, en: String, bnText: String) = CancelQuoteDto(false, b.totalAmount, 0, 0, 0, reason, en, bn(bnText))
        if (b.status == "held") {
            return CancelQuoteDto(true, 0, 0, 0, 0, null, "Nothing has been paid yet; the seats are simply released.", "এখনো কোনো টাকা দেওয়া হয়নি; সিটগুলো ছেড়ে দেওয়া হবে।")
        }
        if (b.status != "ticketed") return no("not_cancellable", "This booking can't be cancelled.", "এই বুকিংটি বাতিল করা যাবে না।")
        if (hours <= 0) return no("already_departed", "This trip has already started.", "এই যাত্রা ইতিমধ্যে শুরু হয়ে গেছে।")
        val service = b.serviceCharge
        val refundable = maxOf(0, b.totalAmount - service)
        if (b.mode == "flight") {
            val rules = b.fareRules
            val deadlineH = rules?.deadlineHours ?: 3
            val deadline = first.minusHours(deadlineH.toLong()).toString()
            if (hours < deadlineH) {
                return no("too_late", "Flights on this fare can only be cancelled up to $deadlineH hours before departure.", "এই ভাড়ার ফ্লাইট যাত্রার $deadlineH ঘণ্টা আগে পর্যন্ত বাতিল করা যায়।")
            }
            if (rules?.refundable != true) {
                return CancelQuoteDto(true, b.totalAmount, 0, refundable, service, "non_refundable",
                    "This fare is non-refundable: you can cancel, but no money comes back.", "এই ভাড়া রিফান্ডযোগ্য নয়: বাতিল করা যাবে, কিন্তু কোনো টাকা ফেরত আসবে না।", deadline)
            }
            val fee = minOf(refundable, (rules.refundFee ?: 0) * b.passengers.size)
            return CancelQuoteDto(true, b.totalAmount, refundable - fee, fee, service, null,
                "Refund fee per passenger from the fare rules; the service charge is not refunded.", "ভাড়ার নিয়ম অনুযায়ী প্রতি যাত্রী রিফান্ড ফি কাটা হবে; সার্ভিস চার্জ ফেরত হয় না।", deadline)
        }
        val policy = if (b.mode == "train") listOf(48 to 0.10, 24 to 0.25, 12 to 0.50) else listOf(24 to 0.10, 12 to 0.50)
        val lastH = policy.last().first
        val deadline = first.minusHours(lastH.toLong()).toString()
        for ((minHours, fraction) in policy) {
            if (hours >= minHours) {
                val fee = Math.round(refundable * fraction / 100) * 100
                val pct = (fraction * 100).toInt()
                return CancelQuoteDto(true, b.totalAmount, refundable - fee, fee, service, null,
                    "$pct% cancellation fee ($minHours+ hours before departure); the service charge is not refunded.",
                    bn("$pct% বাতিল ফি (যাত্রার $minHours+ ঘণ্টা আগে); সার্ভিস চার্জ ফেরত হয় না।"), deadline)
            }
        }
        return no("too_late", "Tickets can be cancelled up to $lastH hours before departure.", "যাত্রার $lastH ঘণ্টা আগে পর্যন্ত টিকিট বাতিল করা যায়।")
    }

    // ---- offers & promo codes (same launch codes and rules as the backend) ----

    val offers: List<OfferDto> = listOf(
        OfferDto("o1", "JATRI150", "৳150 off your first bus ticket", "প্রথম বাস টিকিটে ৳১৫০ ছাড়", "New users only · fares from ৳500",
            "শুধু নতুন ব্যবহারকারীদের জন্য · ভাড়া ৳৫০০ থেকে", "flat", 15000, null, 50000, listOf("bus"), emptyList(), true, "2027-12-31"),
        OfferDto("o2", "FLY8", "8% off flights", "ফ্লাইটে ৮% ছাড়", "Pay with bKash · up to ৳800", "বিকাশে পেমেন্টে · সর্বোচ্চ ৳৮০০",
            "percent", 8, 80000, 0, listOf("flight"), listOf("bkash"), false, "2027-12-31"),
        OfferDto("o3", "RAIL50", "৳50 off train tickets", "ট্রেন টিকিটে ৳৫০ ছাড়", "Pay with Nagad · fares from ৳300", "নগদে পেমেন্টে · ভাড়া ৳৩০০ থেকে",
            "flat", 5000, null, 30000, listOf("train"), listOf("nagad"), false, "2027-12-31"),
        OfferDto("o4", "WELCOME100", "৳100 off your first trip", "প্রথম যাত্রায় ৳১০০ ছাড়", "Flight, bus or train · from ৳1,000",
            "ফ্লাইট, বাস বা ট্রেন · ৳১,০০০ থেকে", "flat", 10000, null, 100000, emptyList(), emptyList(), true, "2027-12-31"),
    )

    fun applyPromo(id: String, code: String): BookingDto {
        val b = get(id)
        if (b.status != "held") throw api("promo_not_allowed", "Codes can only be added before paying.", "পেমেন্টের আগেই শুধু কোড যোগ করা যায়।")
        val o = offers.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
            ?: throw api("promo_invalid", "This promo code doesn't exist or has ended.", "এই প্রোমো কোডটি নেই বা মেয়াদ শেষ।")
        if (o.modes.isNotEmpty() && b.mode !in o.modes) throw api("promo_wrong_mode", "This code isn't valid for this kind of ticket.", "এই কোডটি এই ধরনের টিকিটে চলবে না।")
        val fare = b.baseFare + b.taxes
        if (fare < o.minAmount) {
            val taka = "%,d".format(o.minAmount / 100)
            throw api("promo_min_amount", "This code needs a fare of at least ৳ $taka.", bn("এই কোডের জন্য ভাড়া অন্তত ৳ $taka হতে হবে।"))
        }
        val paidBefore = bookings.values.any { it.id != id && it.status in setOf("ticketed", "refunded") }
        if (o.firstBookingOnly && paidBefore) throw api("promo_first_booking", "This code is for your first booking only.", "এই কোডটি শুধু প্রথম বুকিংয়ের জন্য।")
        var d = if (o.discountType == "percent") fare * o.value / 100 else o.value
        o.maxDiscount?.let { d = minOf(d, it) }
        d = minOf(d, fare) / 100 * 100
        val updated = b.copy(discount = d, totalAmount = b.totalAmount + b.discount - d, promoCode = o.code)
        bookings[id] = updated
        return updated
    }

    fun removePromo(id: String): BookingDto {
        val b = get(id)
        if (b.status != "held") throw api("promo_not_allowed", "Codes can only be added before paying.", "পেমেন্টের আগেই শুধু কোড যোগ করা যায়।")
        return b.copy(discount = 0, totalAmount = b.totalAmount + b.discount, promoCode = null).also { bookings[id] = it }
    }

    // ---- inbox (the backend's notifications) ----

    val inbox = mutableListOf<NotificationDto>()

    private fun notify(type: String, titleEn: String, bodyEn: String, titleBn: String, bodyBn: String, bookingId: String) {
        inbox.add(
            0,
            NotificationDto(
                UUID.randomUUID().toString(), type, titleEn, bn(titleBn), bodyEn, bn(bodyBn),
                JsonObject(mapOf("booking_id" to JsonPrimitive(bookingId))), false, OffsetDateTime.now(ZoneOffset.UTC).toString(),
            ),
        )
    }

    /** Bangla digits, but codes with letters ("JTM96Y") stay as printed. */
    private fun bn(text: String): String = Regex("[A-Za-z0-9]*\\d[A-Za-z0-9]*").replace(text) { m ->
        if (m.value.any { it.isLetter() }) m.value else m.value.map { if (it.isDigit()) '০' + (it - '0') else it }.joinToString("")
    }

    private val payments = mutableMapOf<String, PaymentStatusDto>()
    private val paymentAmounts = mutableMapOf<String, Long>()

    fun initPayment(bookingId: String, method: String = "bkash"): PaymentInitDto {
        val b = get(bookingId)
        if (b.status != "held") {
            throw api("booking_not_payable", "This booking can no longer be paid. Please choose seats again.", "এই বুকিংয়ের পেমেন্ট আর করা যাবে না। আবার সিট বেছে নিন।")
        }
        offers.firstOrNull { it.code == b.promoCode }?.takeIf { it.methods.isNotEmpty() && method !in it.methods }?.let { o ->
            val en = o.methods.joinToString(" or ") { mapOf("bkash" to "bKash", "nagad" to "Nagad", "rocket" to "Rocket", "card" to "card")[it] ?: it }
            val bnNames = o.methods.joinToString(" বা ") { mapOf("bkash" to "বিকাশ", "nagad" to "নগদ", "rocket" to "রকেট", "card" to "কার্ড")[it] ?: it }
            throw api("promo_method", "Your promo code needs payment by $en.", "আপনার প্রোমো কোডের জন্য $bnNames দিয়ে পেমেন্ট করতে হবে।")
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
            val b = get(p.bookingId)
            notify("payment_failed", "Payment didn't go through", "Booking ${b.bookingRef} is still held. You can try paying again.",
                "পেমেন্ট সম্পন্ন হয়নি", "বুকিং ${b.bookingRef} এখনো সংরক্ষিত আছে। আবার পেমেন্ট করতে পারেন।", b.id)
            return
        }
        val b = get(p.bookingId)
        val pnr = "PNR-" + id.take(8).uppercase()
        bookings[b.id] = b.copy(
            status = "ticketed",
            holdExpiresAt = null,
            passengers = b.passengers.mapIndexed { i, pass ->
                // Airlines issue a 13-digit e-ticket per passenger (000 = no real airline).
                val number = if (b.mode == "flight") "000-%010d".format(Math.floorMod((pnr + i).hashCode().toLong() * 7919, 10_000_000_000L)) else "$pnr-${i + 1}"
                pass.copy(ticketNumber = number)
            },
            ticket = TicketDto(qrPayload = "JT1.${b.bookingRef}.${b.id.replace("-", "")}.demo", pdfUrl = "", issuedAt = OffsetDateTime.now(ZoneOffset.UTC).toString()),
        )
        release(b.id)
        payments[id] = p.copy(status = "success", bookingStatus = "ticketed")
        notify("booking_confirmed", "Ticket confirmed 🎉", "Booking ${b.bookingRef}. Your e-ticket is in My Trips.",
            "টিকিট কনফার্ম হয়েছে 🎉", "বুকিং ${b.bookingRef}। ই-টিকিট আমার যাত্রায় আছে।", b.id)
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
