package com.jatri.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BusSearchRequest(
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val date: String,
    @SerialName("coach_type") val coachType: String? = null,
)

@Serializable
data class StopPointDto(
    val id: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_bn") val nameBn: String,
    val time: String,
)

@Serializable
data class BusTripDto(
    val id: String,
    @SerialName("operator_name") val operatorName: String,
    @SerialName("operator_logo_url") val operatorLogoUrl: String? = null,
    @SerialName("coach_number") val coachNumber: String,
    @SerialName("coach_type") val coachType: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    @SerialName("depart_at") val departAt: String,
    @SerialName("arrive_at") val arriveAt: String,
    /** Per seat, in paisa. */
    val fare: Long,
    @SerialName("seats_available") val seatsAvailable: Int,
    @SerialName("boarding_points") val boardingPoints: List<StopPointDto>,
    @SerialName("dropping_points") val droppingPoints: List<StopPointDto>,
)

@Serializable
data class SeatDto(val number: String, val state: String)

@Serializable
data class SeatMapDto(
    val trip: BusTripDto,
    val layout: String,
    val rows: List<List<SeatDto?>>,
    @SerialName("max_selectable") val maxSelectable: Int,
    @SerialName("service_charge_per_seat") val serviceChargePerSeat: Long,
)

@Serializable
data class PassengerInDto(
    @SerialName("full_name") val fullName: String,
    val gender: String,
    val age: Int? = null,
    @SerialName("seat_no") val seatNo: String,
    @SerialName("document_no") val documentNo: String? = null,
)

@Serializable
data class BookingCreateRequest(
    val mode: String = "bus",
    @SerialName("trip_id") val tripId: String,
    val seats: List<String>,
    @SerialName("boarding_point_id") val boardingPointId: String,
    @SerialName("dropping_point_id") val droppingPointId: String,
    val passengers: List<PassengerInDto>,
    @SerialName("contact_phone") val contactPhone: String,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("expected_total") val expectedTotal: Long? = null,
)

@Serializable
data class SegmentDto(
    @SerialName("trip_id") val tripId: String,
    val carrier: String,
    val number: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    @SerialName("depart_at") val departAt: String,
    @SerialName("arrive_at") val arriveAt: String,
    @SerialName("travel_class") val travelClass: String,
    @SerialName("seat_numbers") val seatNumbers: List<String>,
    @SerialName("boarding_point") val boardingPoint: StopPointDto? = null,
    @SerialName("dropping_point") val droppingPoint: StopPointDto? = null,
)

@Serializable
data class PassengerOutDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("ticket_number") val ticketNumber: String? = null,
    val type: String,
    val gender: String,
    val age: Int? = null,
    @SerialName("seat_no") val seatNo: String? = null,
    @SerialName("document_masked") val documentMasked: String? = null,
)

@Serializable
data class BookingDto(
    val id: String,
    @SerialName("booking_ref") val bookingRef: String,
    val mode: String,
    val status: String,
    @SerialName("hold_expires_at") val holdExpiresAt: String? = null,
    @SerialName("base_fare") val baseFare: Long,
    @SerialName("service_charge") val serviceCharge: Long,
    val discount: Long,
    @SerialName("total_amount") val totalAmount: Long,
    val currency: String,
    @SerialName("contact_phone") val contactPhone: String,
    @SerialName("contact_email") val contactEmail: String? = null,
    val segments: List<SegmentDto>,
    val passengers: List<PassengerOutDto>,
    @SerialName("created_at") val createdAt: String,
    val ticket: TicketDto? = null,
)

@Serializable
data class TicketDto(
    @SerialName("qr_payload") val qrPayload: String,
    @SerialName("pdf_url") val pdfUrl: String,
    @SerialName("issued_at") val issuedAt: String,
)

@Serializable
data class PaymentInitRequest(
    @SerialName("booking_id") val bookingId: String,
    val method: String,
)

@Serializable
data class PaymentInitDto(
    @SerialName("payment_id") val paymentId: String,
    @SerialName("checkout_url") val checkoutUrl: String,
    val amount: Long,
    val status: String,
)

@Serializable
data class PaymentStatusDto(
    @SerialName("payment_id") val paymentId: String,
    val status: String,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("booking_id") val bookingId: String,
    @SerialName("booking_status") val bookingStatus: String,
    @SerialName("booking_ref") val bookingRef: String,
)
