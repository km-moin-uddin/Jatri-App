package com.jatri.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OfferDto(
    val id: String,
    val code: String,
    @SerialName("title_en") val titleEn: String,
    @SerialName("title_bn") val titleBn: String,
    @SerialName("subtitle_en") val subtitleEn: String,
    @SerialName("subtitle_bn") val subtitleBn: String,
    @SerialName("discount_type") val discountType: String,
    /** Flat: paisa. Percent: whole percent. */
    val value: Long,
    @SerialName("max_discount") val maxDiscount: Long? = null,
    @SerialName("min_amount") val minAmount: Long = 0,
    val modes: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    @SerialName("first_booking_only") val firstBookingOnly: Boolean = false,
    @SerialName("valid_to") val validTo: String,
)

@Serializable
data class PromoRequest(val code: String)

@Serializable
data class TravellerDto(
    val id: String? = null,
    @SerialName("full_name") val fullName: String,
    @SerialName("given_name") val givenName: String? = null,
    val surname: String? = null,
    val gender: String,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val nationality: String = "BD",
    @SerialName("nid_number") val nidNumber: String? = null,
    @SerialName("passport_no") val passportNo: String? = null,
    @SerialName("passport_expiry") val passportExpiry: String? = null,
    val type: String? = null,
)

@Serializable
data class PriceAlertRequest(
    val mode: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val date: String,
    @SerialName("target_price") val targetPrice: Long,
)

@Serializable
data class PriceAlertDto(
    val id: String,
    val mode: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val date: String,
    @SerialName("target_price") val targetPrice: Long,
    @SerialName("last_price") val lastPrice: Long? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
)
