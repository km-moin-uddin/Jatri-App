package com.jatri.core.data.repository

import android.content.Context
import com.jatri.core.network.model.BookingDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's bookings as the server last sent them, in app-private storage, so My Trips and
 * tickets (with their QR codes) open without internet. Cleared on logout.
 */
@Singleton
class BookingCache internal constructor(private val file: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "bookings.json"))

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = ListSerializer(BookingDto.serializer())
    private val lock = Mutex()

    suspend fun all(): List<BookingDto> = lock.withLock { read() }

    suspend fun get(id: String): BookingDto? = all().firstOrNull { it.id == id }

    suspend fun replaceAll(bookings: List<BookingDto>) = lock.withLock { write(bookings) }

    suspend fun put(booking: BookingDto) = lock.withLock {
        val list = read()
        write(if (list.any { it.id == booking.id }) list.map { if (it.id == booking.id) booking else it } else listOf(booking) + list)
    }

    suspend fun clear() = lock.withLock { withContext(Dispatchers.IO) { file.delete() } }

    private suspend fun read(): List<BookingDto> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList() else runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    private suspend fun write(list: List<BookingDto>) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, list))
        tmp.renameTo(file) // atomic: a crash never leaves half a file
    }
}
