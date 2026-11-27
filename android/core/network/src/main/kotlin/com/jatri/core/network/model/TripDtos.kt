package com.jatri.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CancelQuoteDto(
    val cancellable: Boolean,
    val paid: Long,
    val refund: Long,
    val fee: Long,
    @SerialName("non_refundable") val nonRefundable: Long,
    val reason: String? = null,
    @SerialName("message_en") val messageEn: String,
    @SerialName("message_bn") val messageBn: String,
    val deadline: String? = null,
)

@Serializable
data class CancelRequest(@SerialName("expected_refund") val expectedRefund: Long? = null)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    @SerialName("title_en") val titleEn: String,
    @SerialName("title_bn") val titleBn: String,
    @SerialName("body_en") val bodyEn: String,
    @SerialName("body_bn") val bodyBn: String,
    val data: JsonObject = JsonObject(emptyMap()),
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NotificationPageDto(val items: List<NotificationDto>, val unread: Int)

@Serializable
data class DeviceRequest(val token: String, val platform: String = "android")
