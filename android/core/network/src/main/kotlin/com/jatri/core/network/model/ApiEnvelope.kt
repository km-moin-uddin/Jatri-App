package com.jatri.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every backend response uses this envelope:
 * `{ "success": bool, "data": ..., "error": { "code", "message_en", "message_bn" } }`.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val code: String,
    @SerialName("message_en") val messageEn: String,
    @SerialName("message_bn") val messageBn: String,
)

/** Thrown when the backend answers `success = false`. */
class ApiException(val error: ApiError) : Exception(error.messageEn)

fun <T> ApiEnvelope<T>.requireData(): T {
    if (success && data != null) return data
    throw ApiException(error ?: ApiError("unknown", "Unknown error", "অজানা সমস্যা"))
}
