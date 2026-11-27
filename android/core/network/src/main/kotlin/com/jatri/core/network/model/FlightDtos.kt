package com.jatri.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SliceDto(
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val date: String,
)

@Serializable
data class FlightSearchRequest(
    val slices: List<SliceDto>,
    val adults: Int,
    val children: Int,
    val infants: Int,
    val cabin: String,
)

@Serializable
data class FlightLegDto(
    @SerialName("carrier_code") val carrierCode: String,
    @SerialName("carrier_name") val carrierName: String,
    @SerialName("carrier_logo_url") val carrierLogoUrl: String? = null,
    @SerialName("flight_number") val flightNumber: String,
    val aircraft: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    @SerialName("from_code") val fromCode: String,
    @SerialName("to_code") val toCode: String,
    /** Local time at the airport, with its UTC offset. */
    @SerialName("depart_at") val departAt: String,
    @SerialName("arrive_at") val arriveAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
)

@Serializable
data class FlightSliceDto(
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    @SerialName("depart_at") val departAt: String,
    @SerialName("arrive_at") val arriveAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val stops: Int,
    val legs: List<FlightLegDto>,
)

@Serializable
data class FareRulesDto(
    val refundable: Boolean,
    @SerialName("refund_fee") val refundFee: Long? = null,
    @SerialName("change_fee") val changeFee: Long? = null,
    @SerialName("deadline_hours") val deadlineHours: Int,
    @SerialName("checked_baggage_kg") val checkedBaggageKg: Int,
    @SerialName("cabin_baggage_kg") val cabinBaggageKg: Int,
    @SerialName("no_show") val noShow: String = "forfeit",
)

@Serializable
data class PaxFareDto(val type: String, val count: Int, val base: Long, val tax: Long)

@Serializable
data class FlightOfferDto(
    val id: String,
    val slices: List<FlightSliceDto>,
    val cabin: String,
    @SerialName("fare_brand") val fareBrand: String,
    val rules: FareRulesDto,
    val fares: List<PaxFareDto>,
    @SerialName("base_total") val baseTotal: Long,
    @SerialName("tax_total") val taxTotal: Long,
    @SerialName("service_charge") val serviceCharge: Long,
    val total: Long,
    @SerialName("seats_left") val seatsLeft: Int,
    val international: Boolean,
)

@Serializable
data class FlightPassengerInDto(
    val type: String,
    @SerialName("given_name") val givenName: String,
    val surname: String,
    val gender: String,
    @SerialName("date_of_birth") val dateOfBirth: String,
    val nationality: String,
    @SerialName("document_no") val documentNo: String? = null,
    @SerialName("document_expiry") val documentExpiry: String? = null,
)

/** POST /bookings with mode "flight" (no default: the JSON encoder skips defaults). */
@Serializable
data class FlightBookingRequest(
    val mode: String,
    @SerialName("offer_id") val offerId: String,
    val passengers: List<FlightPassengerInDto>,
    @SerialName("contact_phone") val contactPhone: String,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("expected_total") val expectedTotal: Long? = null,
)
