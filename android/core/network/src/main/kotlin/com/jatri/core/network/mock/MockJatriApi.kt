package com.jatri.core.network.mock

import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.AuthResponse
import com.jatri.core.network.model.LocationDto
import com.jatri.core.network.model.OtpSendRequest
import com.jatri.core.network.model.OtpSendResponse
import com.jatri.core.network.model.OtpVerifyRequest
import com.jatri.core.network.model.RefreshRequest
import com.jatri.core.network.model.TokenPairDto
import com.jatri.core.network.model.UserDto
import com.jatri.core.network.model.UserUpdateRequest
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MOCK BACKEND for builds without a server (default for now, so the APK works on any phone).
 * Mirrors the real API's rules and bilingual errors. The OTP is always [MOCK_OTP].
 * Data lives in memory only; the app's own session store keeps you logged in across restarts.
 */
@Singleton
class MockJatriApi @Inject constructor() : JatriApi {
    private val users = mutableMapOf<String, UserDto>() // by phone
    private val refreshTokens = mutableMapOf<String, String>() // token -> phone
    private val accessTokens = mutableMapOf<String, String>() // token -> phone
    var currentAccessToken: String? = null // set by the session layer via header in the real API

    override suspend fun sendOtp(body: OtpSendRequest): ApiEnvelope<OtpSendResponse> {
        delay(LATENCY)
        val phone = normalize(body.phone) ?: throw api("invalid_phone", "Enter a valid Bangladeshi mobile number.", "সঠিক বাংলাদেশি মোবাইল নম্বর দিন।")
        return ApiEnvelope(true, OtpSendResponse(phone, expiresIn = 300, resendAfter = 60))
    }

    override suspend fun verifyOtp(body: OtpVerifyRequest): ApiEnvelope<AuthResponse> {
        delay(LATENCY)
        val phone = normalize(body.phone) ?: throw api("invalid_phone", "Enter a valid Bangladeshi mobile number.", "সঠিক বাংলাদেশি মোবাইল নম্বর দিন।")
        if (body.code != MOCK_OTP) throw api("otp_invalid", "The code is not correct.", "কোডটি সঠিক নয়।")
        val isNew = phone !in users
        val user = users.getOrPut(phone) { UserDto(id = UUID.randomUUID().toString(), phone = phone) }
        return ApiEnvelope(true, AuthResponse(issue(phone), user, isNew))
    }

    override suspend fun refresh(body: RefreshRequest): ApiEnvelope<TokenPairDto> {
        val phone = refreshTokens.remove(body.refreshToken)
            ?: throw api("invalid_refresh_token", "Your session has ended. Please log in again.", "আপনার সেশন শেষ হয়েছে। আবার লগইন করুন।")
        return ApiEnvelope(true, issue(phone))
    }

    override suspend fun logout(body: RefreshRequest): ApiEnvelope<JsonElement?> {
        refreshTokens.remove(body.refreshToken)
        return ApiEnvelope(true, null)
    }

    override suspend fun me(): ApiEnvelope<UserDto> {
        delay(LATENCY / 2)
        return ApiEnvelope(true, currentUser())
    }

    override suspend fun updateMe(body: UserUpdateRequest): ApiEnvelope<UserDto> {
        delay(LATENCY)
        val user = currentUser()
        val updated = user.copy(
            name = body.name?.trim()?.replace(Regex("\\s+"), " ") ?: user.name,
            email = body.email ?: user.email,
            language = body.language ?: user.language,
        )
        users[user.phone] = updated
        return ApiEnvelope(true, updated)
    }

    override suspend fun deleteMe(): ApiEnvelope<JsonElement?> {
        delay(LATENCY)
        val user = currentUser()
        users.remove(user.phone)
        refreshTokens.values.removeAll { it == user.phone }
        return ApiEnvelope(true, null)
    }

    /** Empty: the app keeps its bundled seed list when the server has nothing to add. */
    override suspend fun locations(type: String?, q: String?): ApiEnvelope<List<LocationDto>> = ApiEnvelope(true, emptyList())

    /**
     * The mock can't see request headers, so after an app restart it rebuilds the user from the
     * stored session the first time it's asked (see MockSessionBridge in core:data).
     */
    fun restore(phone: String, user: UserDto, accessToken: String, refreshToken: String) {
        users.getOrPut(phone) { user }
        accessTokens[accessToken] = phone
        refreshTokens[refreshToken] = phone
        currentAccessToken = accessToken
    }

    private fun currentUser(): UserDto {
        val phone = currentAccessToken?.let(accessTokens::get)
            ?: throw api("unauthorized", "Please log in again.", "অনুগ্রহ করে আবার লগইন করুন।")
        return users[phone] ?: throw api("unauthorized", "Please log in again.", "অনুগ্রহ করে আবার লগইন করুন।")
    }

    private fun issue(phone: String): TokenPairDto {
        val access = "mock-access-" + UUID.randomUUID()
        val refresh = "mock-refresh-" + UUID.randomUUID()
        accessTokens[access] = phone
        refreshTokens[refresh] = phone
        currentAccessToken = access
        return TokenPairDto(access, refresh, expiresIn = 900)
    }

    private fun normalize(raw: String): String? {
        val digits = raw.filter(Char::isDigit).let { if (it.startsWith("880")) it.drop(2) else it }
        return digits.takeIf { Regex("^01[3-9]\\d{8}$").matches(it) }
    }

    private fun api(code: String, en: String, bn: String) = AppException(AppError.Api(code, en, bn))

    companion object {
        const val MOCK_OTP = "123456"
        private const val LATENCY = 600L
    }
}
