package com.jatri.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrainSearchRequest(
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val date: String,
    @SerialName("travel_class") val travelClass: String? = null,
)

@Serializable
data class TrainClassDto(
    @SerialName("travel_class") val travelClass: String,
    /** Per seat, paisa. */
    val fare: Long,
    @SerialName("seats_available") val seatsAvailable: Int,
)

@Serializable
data class TrainTripDto(
    val id: String,
    @SerialName("train_name") val trainName: String,
    @SerialName("train_number") val trainNumber: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    @SerialName("depart_at") val departAt: String,
    @SerialName("arrive_at") val arriveAt: String,
    /** "tuesday" etc., or null when the train runs daily. */
    @SerialName("off_day") val offDay: String? = null,
    val classes: List<TrainClassDto>,
)

@Serializable
data class CoachDto(val name: String, val layout: String, val rows: List<List<SeatDto?>>)

@Serializable
data class TrainSeatMapDto(
    val trip: TrainTripDto,
    @SerialName("travel_class") val travelClass: String,
    val fare: Long,
    val coaches: List<CoachDto>,
    @SerialName("max_selectable") val maxSelectable: Int,
    @SerialName("service_charge_per_seat") val serviceChargePerSeat: Long,
)

@Serializable
data class TrainPassengerInDto(
    @SerialName("full_name") val fullName: String,
    val gender: String,
    val age: Int,
    @SerialName("seat_no") val seatNo: String,
    @SerialName("document_no") val documentNo: String? = null,
)

/** POST /bookings with mode "train" (no default: the JSON encoder skips defaults). */
@Serializable
data class TrainBookingRequest(
    val mode: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("travel_class") val travelClass: String,
    val seats: List<String>,
    val passengers: List<TrainPassengerInDto>,
    @SerialName("contact_phone") val contactPhone: String,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("expected_total") val expectedTotal: Long? = null,
)
