package com.jatri.core.network.api

import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.AuthResponse
import com.jatri.core.network.model.BookingCreateRequest
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.BusSearchRequest
import com.jatri.core.network.model.BusTripDto
import com.jatri.core.network.model.SeatMapDto
import com.jatri.core.network.model.LocationDto
import com.jatri.core.network.model.OtpSendRequest
import com.jatri.core.network.model.PaymentInitDto
import com.jatri.core.network.model.PaymentInitRequest
import com.jatri.core.network.model.PaymentStatusDto
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
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody

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

    @POST("buses/search")
    suspend fun searchBuses(@Body body: BusSearchRequest): ApiEnvelope<List<BusTripDto>>

    @GET("buses/{tripId}/seats")
    suspend fun busSeats(@Path("tripId") tripId: String): ApiEnvelope<SeatMapDto>

    @POST("bookings")
    suspend fun createBooking(
        @Body body: BookingCreateRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ApiEnvelope<BookingDto>

    @GET("bookings")
    suspend fun bookings(): ApiEnvelope<List<BookingDto>>

    @GET("bookings/{id}")
    suspend fun booking(@Path("id") id: String): ApiEnvelope<BookingDto>

    @POST("bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: String): ApiEnvelope<BookingDto>

    @POST("payments/init")
    suspend fun initPayment(
        @Body body: PaymentInitRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ApiEnvelope<PaymentInitDto>

    @GET("payments/{id}/status")
    suspend fun paymentStatus(@Path("id") id: String): ApiEnvelope<PaymentStatusDto>

    /** Raw PDF bytes (not an envelope). */
    @Streaming
    @GET("tickets/{bookingId}/pdf")
    suspend fun ticketPdf(@Path("bookingId") bookingId: String): ResponseBody
}
