package com.jatri.core.data.repository

import com.jatri.core.model.AppNotification
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.apiCall
import com.jatri.core.network.model.DeviceRequest
import com.jatri.core.network.model.NotificationDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationRepository {
    /** Unread count for the bell badge; refreshed by [inbox] and the read calls. */
    val unread: StateFlow<Int>

    suspend fun inbox(): List<AppNotification>
    suspend fun markRead(id: String)
    suspend fun markAllRead()

    /** FCM token of this phone (when Firebase is set up in the build). */
    suspend fun registerDevice(token: String)
    suspend fun unregisterDevice(token: String)
}

@Singleton
internal class NetworkNotificationRepository @Inject constructor(private val api: JatriApi) : NotificationRepository {
    private val _unread = MutableStateFlow(0)
    override val unread: StateFlow<Int> = _unread.asStateFlow()

    override suspend fun inbox(): List<AppNotification> {
        val page = apiCall { api.notifications() }
        _unread.value = page.unread
        return page.items.map { it.toModel() }
    }

    override suspend fun markRead(id: String) {
        apiCall { api.markNotificationRead(id) }
        _unread.value = (_unread.value - 1).coerceAtLeast(0)
    }

    override suspend fun markAllRead() {
        apiCall { api.markAllNotificationsRead() }
        _unread.value = 0
    }

    override suspend fun registerDevice(token: String) {
        apiCall { api.registerDevice(DeviceRequest(token)) }
    }

    override suspend fun unregisterDevice(token: String) {
        apiCall { api.unregisterDevice(token) }
    }
}

internal fun NotificationDto.toModel() = AppNotification(
    id = id,
    type = type,
    titleEn = titleEn,
    titleBn = titleBn,
    bodyEn = bodyEn,
    bodyBn = bodyBn,
    bookingId = data["booking_id"]?.jsonPrimitive?.contentOrNull,
    isRead = isRead,
    createdAt = parseInstant(createdAt),
    data = data.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull.orEmpty() },
)

/** For contract tests in this module's test source set. */
internal fun toModelForTest(dto: NotificationDto) = dto.toModel()
