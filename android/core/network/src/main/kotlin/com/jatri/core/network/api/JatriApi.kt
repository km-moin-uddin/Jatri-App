package com.jatri.core.network.api

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
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/** Backend `/api/v1`. Every call returns the `{success, data, error}` envelope. */
interface JatriApi {
    @POST("auth/otp/send")
    suspend fun sendOtp(@Body body: OtpSendRequest): ApiEnvelope<OtpSendResponse>

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequest): ApiEnvelope<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiEnvelope<TokenPairDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest): ApiEnvelope<JsonElement?>

    @GET("me")
    suspend fun me(): ApiEnvelope<UserDto>

    @PATCH("me")
    suspend fun updateMe(@Body body: UserUpdateRequest): ApiEnvelope<UserDto>

    @DELETE("me")
    suspend fun deleteMe(): ApiEnvelope<JsonElement?>

    @GET("locations")
    suspend fun locations(@Query("type") type: String? = null, @Query("q") q: String? = null): ApiEnvelope<List<LocationDto>>
}
