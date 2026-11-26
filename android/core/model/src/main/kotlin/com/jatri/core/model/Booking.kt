package com.jatri.core.model

import java.time.Instant
import java.time.LocalDateTime

enum class BookingStatus { PENDING, HELD, CONFIRMED, TICKETED, CANCELLED, REFUNDED, FAILED }

data class BookingSegment(
    val carrier: String,
    val number: String,
    val fromId: String,
    val toId: String,
    val departAt: LocalDateTime,
    val arriveAt: LocalDateTime,
    val travelClass: String,
    val seatNumbers: List<String>,
    val boardingPoint: StopPoint?,
    val droppingPoint: StopPoint?,
)

data class BookingPassenger(
    val fullName: String,
    val gender: Gender,
    val age: Int?,
    val seatNo: String?,
    val documentMasked: String?,
    val ticketNumber: String? = null,
)

/** The e-ticket. [qrPayload] is signed by the server, so counters can check it offline. */
data class Ticket(val qrPayload: String, val pdfUrl: String, val issuedAt: Instant)

data class Booking(
    val id: String,
    val ref: String,
    val mode: TravelMode,
    val status: BookingStatus,
    val holdExpiresAt: Instant?,
    val baseFare: Money,
    val serviceCharge: Money,
    val discount: Money,
    val total: Money,
    val contactPhone: String,
    val contactEmail: String?,
    val segments: List<BookingSegment>,
    val passengers: List<BookingPassenger>,
    val ticket: Ticket? = null,
)

enum class PaymentMethod { BKASH, NAGAD, ROCKET, CARD }

enum class PaymentStatus { INITIATED, SUCCESS, FAILED, CANCELLED, REFUND_PENDING, REFUNDED }

/** A started payment: open [checkoutUrl] (gateway page), then watch the status. */
data class PaymentStart(val paymentId: String, val checkoutUrl: String, val amount: Money)

data class PaymentState(
    val paymentId: String,
    val status: PaymentStatus,
    val failureReason: String?,
    val bookingId: String,
    val bookingStatus: BookingStatus,
    val bookingRef: String,
)

/** What the passenger form sends to hold seats. */
data class BusHoldRequest(
    val tripId: String,
    val seats: List<String>,
    val boardingPointId: String,
    val droppingPointId: String,
    val passengers: List<PassengerInput>,
    val contactPhone: String,
    val contactEmail: String?,
    val expectedTotal: Money,
    /** Same key for retries of the same tap, so a flaky network can't hold seats twice. */
    val idempotencyKey: String,
)

data class PassengerInput(
    val fullName: String,
    val gender: Gender,
    val age: Int?,
    val seatNo: String,
    val nid: String? = null,
)
