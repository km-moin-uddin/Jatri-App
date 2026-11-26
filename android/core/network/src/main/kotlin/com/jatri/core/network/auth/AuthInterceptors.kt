package com.jatri.core.network.auth

import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.RefreshRequest
import com.jatri.core.network.model.TokenPairDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

private const val AUTH_PATH = "/auth/"

/** Adds `Authorization: Bearer <access token>` to every call except the auth endpoints. */
class AuthHeaderInterceptor @Inject constructor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains(AUTH_PATH)) return chain.proceed(request)
        val token = runBlocking { store.current()?.accessToken } ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/** Emits when the session could not be refreshed, so the app can show the login screen. */
@Singleton
class SessionEvents @Inject constructor() {
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired
    internal fun notifyExpired() {
        _expired.tryEmit(Unit)
    }
}

/**
 * On a 401, swaps the refresh token for a new pair once and retries the request. Concurrent 401s
 * share one refresh (the lock + "token already changed" check). If refresh fails the session is
 * cleared and [SessionEvents.expired] fires.
 */
class TokenAuthenticator(
    private val store: SessionStore,
    private val events: SessionEvents,
    private val baseUrl: String,
    private val refreshClient: OkHttpClient,
    private val json: Json,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains(AUTH_PATH)) return null
        if (responseCount(response) >= 2) return null
        val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(lock) {
            val current = runBlocking { store.current() } ?: return null
            // Another request already refreshed while we waited for the lock.
            if (current.accessToken != sentToken) return response.request.withToken(current.accessToken)

            val pair = callRefresh(current.refreshToken)
            if (pair == null) {
                runBlocking { store.clear() }
                events.notifyExpired()
                return null
            }
            runBlocking { store.save(current.copy(accessToken = pair.accessToken, refreshToken = pair.refreshToken)) }
            return response.request.withToken(pair.accessToken)
        }
    }

    private fun callRefresh(refreshToken: String): TokenPairDto? = runCatching {
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/auth/refresh").post(body).build()
        refreshClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            json.decodeFromString(ApiEnvelope.serializer(TokenPairDto.serializer()), resp.body.string()).data
        }
    }.getOrNull()

    private fun Request.withToken(token: String) = newBuilder().header("Authorization", "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
