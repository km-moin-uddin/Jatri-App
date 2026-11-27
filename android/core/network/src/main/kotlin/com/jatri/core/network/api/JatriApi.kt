package com.jatri.core.network.api

import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.AuthResponse
import com.jatri.core.network.model.BookingCreateRequest
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.CancelQuoteDto
import com.jatri.core.network.model.CancelRequest
import com.jatri.core.network.model.DeviceRequest
import com.jatri.core.network.model.NotificationDto
import com.jatri.core.network.model.NotificationPageDto
import com.jatri.core.network.model.BusSearchRequest
import com.jatri.core.network.model.BusTripDto
import com.jatri.core.network.model.FlightBookingRequest
import com.jatri.core.network.model.FlightOfferDto
import com.jatri.core.network.model.FlightSearchRequest
import com.jatri.core.network.model.TrainBookingRequest
import com.jatri.core.network.model.TrainSearchRequest
import com.jatri.core.network.model.TrainSeatMapDto
import com.jatri.core.network.model.TrainTripDto
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

    @POST("trains/search")
    suspend fun searchTrains(@Body body: TrainSearchRequest): ApiEnvelope<List<TrainTripDto>>

    @GET("trains/{tripId}/seats")
    suspend fun trainSeats(@Path("tripId") tripId: String, @Query("travel_class") travelClass: String): ApiEnvelope<TrainSeatMapDto>

    @POST("bookings")
    suspend fun createTrainBooking(
        @Body body: TrainBookingRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ApiEnvelope<BookingDto>

    @POST("flights/search")
    suspend fun searchFlights(@Body body: FlightSearchRequest): ApiEnvelope<List<FlightOfferDto>>

    @GET("flights/{offerId}")
    suspend fun flightOffer(@Path("offerId") offerId: String): ApiEnvelope<FlightOfferDto>

    @POST("bookings")
    suspend fun createFlightBooking(
        @Body body: FlightBookingRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ApiEnvelope<BookingDto>

    @POST("bookings")
    suspend fun createBooking(
        @Body body: BookingCreateRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ApiEnvelope<BookingDto>

    @GET("bookings")
    suspend fun bookings(): ApiEnvelope<List<BookingDto>>

    @GET("bookings/{id}")
    suspend fun booking(@Path("id") id: String): ApiEnvelope<BookingDto>

    @GET("bookings/{id}/cancel-quote")
    suspend fun cancelQuote(@Path("id") id: String): ApiEnvelope<CancelQuoteDto>

    @POST("bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: String, @Body body: CancelRequest = CancelRequest()): ApiEnvelope<BookingDto>

    @GET("notifications")
    suspend fun notifications(@Query("limit") limit: Int = 30, @Query("before") before: String? = null): ApiEnvelope<NotificationPageDto>

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): ApiEnvelope<NotificationDto>

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): ApiEnvelope<JsonElement?>

    @POST("devices")
    suspend fun registerDevice(@Body body: DeviceRequest): ApiEnvelope<JsonElement?>

    @DELETE("devices/{token}")
    suspend fun unregisterDevice(@Path("token") token: String): ApiEnvelope<JsonElement?>

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
