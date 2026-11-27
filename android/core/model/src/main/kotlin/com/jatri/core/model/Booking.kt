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
    /** Trains: the coach(es) of the seats. */
    val coach: String? = null,
    /** Flights: which journey this flight belongs to (0 = outbound), aircraft and checked baggage. */
    val journeyIndex: Int = 0,
    val aircraft: String? = null,
    val baggage: String? = null,
)

data class BookingPassenger(
    val fullName: String,
    val gender: Gender,
    val age: Int?,
    val seatNo: String?,
    val documentMasked: String?,
    val ticketNumber: String? = null,
    val type: PassengerType = PassengerType.ADULT,
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
    /** Flights: airport/government taxes, cabin, fare brand and the rules of the fare. */
    val taxes: Money = Money.ZERO,
    val cabinClass: CabinClass? = null,
    val fareBrand: FareBrand? = null,
    val fareRules: FareRules? = null,
    val promoCode: String? = null,
    /** Cancellation & refund (Phase 7). */
    val cancelledAt: Instant? = null,
    val refund: Refund? = null,
) {
    val isFlight: Boolean get() = mode == TravelMode.FLIGHT

    /** When the trip starts (first departure, local wall time). */
    val departAt: LocalDateTime? get() = segments.minOfOrNull { it.departAt }

    /** Flights grouped by journey (outbound, return, ...). */
    val journeys: List<List<BookingSegment>> get() = segments.groupBy { it.journeyIndex }.toSortedMap().values.toList()
}

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

/** What the flight passenger form sends to hold the fare (the airline creates a PNR). */
data class FlightHoldRequest(
    val offerId: String,
    val passengers: List<FlightPassengerInput>,
    val contactPhone: String,
    val contactEmail: String?,
    val expectedTotal: Money,
    val idempotencyKey: String,
)

/** Names exactly as on the passport / NID, in English letters. */
data class FlightPassengerInput(
    val type: PassengerType,
    val givenName: String,
    val surname: String,
    val gender: Gender,
    val dateOfBirth: java.time.LocalDate,
    val nationality: String = "BD",
    val documentNo: String? = null,
    val documentExpiry: java.time.LocalDate? = null,
)

/** What the train passenger form sends to hold seats. NID is required for passengers aged 18+. */
data class TrainHoldRequest(
    val tripId: String,
    val trainClass: TrainClass,
    val seats: List<String>,
    val passengers: List<PassengerInput>,
    val contactPhone: String,
    val contactEmail: String?,
    val expectedTotal: Money,
    val idempotencyKey: String,
)

enum class RefundStatus { PENDING, PROCESSING, REFUNDED, FAILED }

data class Refund(val amount: Money, val fee: Money, val status: RefundStatus, val processedAt: Instant? = null)

/** What cancelling now would give back, keep and why (from the server's fare rules). */
data class CancelQuote(
    val cancellable: Boolean,
    val paid: Money,
    val refund: Money,
    val fee: Money,
    val nonRefundable: Money,
    /** too_late | already_departed | non_refundable | not_cancellable */
    val reason: String?,
    val messageEn: String,
    val messageBn: String,
    val deadline: Instant?,
) {
    fun message(language: AppLanguage) = if (language == AppLanguage.BANGLA) messageBn else messageEn
}

/** One inbox message. [bookingId] opens the ticket when tapped. */
data class AppNotification(
    val id: String,
    val type: String,
    val titleEn: String,
    val titleBn: String,
    val bodyEn: String,
    val bodyBn: String,
    val bookingId: String?,
    val isRead: Boolean,
    val createdAt: Instant,
    /** Extra fields, e.g. a price alert's mode / from_id / to_id / date. */
    val data: Map<String, String> = emptyMap(),
) {
    fun title(language: AppLanguage) = if (language == AppLanguage.BANGLA) titleBn else titleEn
    fun body(language: AppLanguage) = if (language == AppLanguage.BANGLA) bodyBn else bodyEn
}
