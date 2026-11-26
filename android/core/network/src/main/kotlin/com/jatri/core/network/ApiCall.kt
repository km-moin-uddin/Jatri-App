package com.jatri.core.network

import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.model.ApiEnvelope
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Runs an API call and returns its `data`, or throws [AppException] with a UI-ready [AppError]:
 * the backend's bilingual message for API errors, [AppError.Network] when offline or timed out.
 */
suspend fun <T> apiCall(block: suspend () -> ApiEnvelope<T>): T {
    val envelope = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        throw e
    } catch (e: HttpException) {
        throw AppException(e.toAppError())
    } catch (e: IOException) {
        throw AppException(AppError.Network)
    } catch (e: Exception) {
        throw AppException(AppError.Unknown)
    }
    @Suppress("UNCHECKED_CAST")
    return when {
        envelope.success -> envelope.data as T
        envelope.error != null -> throw AppException(envelope.error.toAppError())
        else -> throw AppException(AppError.Unknown)
    }
}

internal fun HttpException.toAppError(): AppError {
    val body = response()?.errorBody()?.string() ?: return AppError.Unknown
    return runCatching { errorJson.decodeFromString(ApiEnvelope.serializer(kotlinx.serialization.json.JsonElement.serializer()), body) }
        .getOrNull()?.error?.toAppError() ?: AppError.Unknown
}

internal fun com.jatri.core.network.model.ApiError.toAppError() = AppError.Api(code, messageEn, messageBn)
