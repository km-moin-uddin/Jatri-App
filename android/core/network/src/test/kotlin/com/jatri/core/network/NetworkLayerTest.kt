package com.jatri.core.network

import com.jatri.core.datastore.session.StoredSession
import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.auth.AuthHeaderInterceptor
import com.jatri.core.network.auth.SessionEvents
import com.jatri.core.network.auth.TokenAuthenticator
import com.jatri.core.network.di.NetworkModule
import com.jatri.core.network.model.OtpVerifyRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Uses response bodies copied from the real FastAPI backend, so DTOs and the app agree. */
class NetworkLayerTest {
    private val server = MockWebServer()
    private val json = NetworkModule.providesJson()
    private lateinit var store: FakeSessionStore
    private lateinit var api: JatriApi
    private val events = SessionEvents()

    private val session = StoredSession("old-access", "old-refresh", "u1", "01712345678")

    @Before
    fun setUp() {
        server.start()
        store = FakeSessionStore(session)
        val baseUrl = server.url("/api/v1/").toString()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthHeaderInterceptor(store))
            .authenticator(TokenAuthenticator(store, events, baseUrl, OkHttpClient(), json))
            .build()
        api = Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(JatriApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()

    private val userJson = """{"id":"35edb4eb-34a6-4e1b-9ba5-3dcf12670f91","phone":"01712345678","name":"করিম","email":null,"language":"bn"}"""

    @Test
    fun `adds bearer token and parses the success envelope`() = runTest {
        server.enqueue(json(200, """{"success":true,"data":$userJson,"error":null}"""))
        val user = apiCall { api.me() }
        assertEquals("করিম", user.name)
        assertEquals("Bearer old-access", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `refreshes once on 401 and retries with the new token`() = runTest {
        server.enqueue(json(401, """{"success":false,"data":null,"error":{"code":"unauthorized","message_en":"Please log in again.","message_bn":"অনুগ্রহ করে আবার লগইন করুন।"}}"""))
        server.enqueue(json(200, """{"success":true,"data":{"access_token":"new-access","refresh_token":"new-refresh","token_type":"bearer","expires_in":900},"error":null}"""))
        server.enqueue(json(200, """{"success":true,"data":$userJson,"error":null}"""))

        apiCall { api.me() }

        assertEquals("Bearer old-access", server.takeRequest().headers["Authorization"])
        val refresh = server.takeRequest()
        assertTrue(refresh.url.encodedPath.endsWith("/auth/refresh"))
        assertTrue(refresh.body!!.utf8().contains("old-refresh"))
        assertEquals("Bearer new-access", server.takeRequest().headers["Authorization"])
        assertEquals("new-refresh", store.current()?.refreshToken)
    }

    @Test
    fun `clears the session when refresh fails`() = runTest {
        server.enqueue(json(401, """{"success":false,"data":null,"error":{"code":"unauthorized","message_en":"x","message_bn":"y"}}"""))
        server.enqueue(json(401, """{"success":false,"data":null,"error":{"code":"invalid_refresh_token","message_en":"x","message_bn":"y"}}"""))
        try {
            apiCall { api.me() }
            fail("expected AppException")
        } catch (e: AppException) {
            assertEquals("unauthorized", (e.error as AppError.Api).code)
        }
        assertNull(runBlocking { store.current() })
    }

    @Test
    fun `maps backend errors to bilingual AppError`() = runTest {
        server.enqueue(json(400, """{"success":false,"data":null,"error":{"code":"otp_invalid","message_en":"The code is not correct.","message_bn":"কোডটি সঠিক নয়।"}}"""))
        try {
            apiCall { api.verifyOtp(OtpVerifyRequest("01712345678", "000000")) }
            fail("expected AppException")
        } catch (e: AppException) {
            assertEquals(AppError.Api("otp_invalid", "The code is not correct.", "কোডটি সঠিক নয়।"), e.error)
        }
    }

    @Test
    fun `network failure becomes AppError Network`() = runTest {
        server.close()
        try {
            apiCall { api.me() }
            fail("expected AppException")
        } catch (e: AppException) {
            assertEquals(AppError.Network, e.error)
        }
    }
}
