package com.jatri.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OtpSendRequest(val phone: String)

@Serializable
data class OtpSendResponse(
    val phone: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("resend_after") val resendAfter: Int,
)

@Serializable
data class OtpVerifyRequest(
    val phone: String,
    val code: String,
    @SerialName("device_info") val deviceInfo: String? = null,
)

@Serializable
data class TokenPairDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class UserDto(
    val id: String,
    val phone: String,
    val name: String? = null,
    val email: String? = null,
    val language: String = "bn",
)

@Serializable
data class AuthResponse(
    val tokens: TokenPairDto,
    val user: UserDto,
    @SerialName("is_new_user") val isNewUser: Boolean,
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class UserUpdateRequest(
    val name: String? = null,
    val email: String? = null,
    val language: String? = null,
)

@Serializable
data class LocationDto(
    val id: String,
    val type: String,
    val code: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_bn") val nameBn: String,
    @SerialName("city_en") val cityEn: String,
    @SerialName("city_bn") val cityBn: String,
    val country: String,
    @SerialName("is_popular") val isPopular: Boolean,
)
