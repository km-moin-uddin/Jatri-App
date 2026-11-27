package com.jatri.core.network.mock

import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.model.ApiEnvelope
import com.jatri.core.network.model.AuthResponse
import com.jatri.core.network.model.BookingCreateRequest
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.BusSearchRequest
import com.jatri.core.network.model.BusTripDto
import com.jatri.core.network.model.CancelQuoteDto
import com.jatri.core.network.model.CancelRequest
import com.jatri.core.network.model.DeviceRequest
import com.jatri.core.network.model.NotificationDto
import com.jatri.core.network.model.NotificationPageDto
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
import com.jatri.core.network.model.OtpSendResponse
import com.jatri.core.network.model.OtpVerifyRequest
import com.jatri.core.network.model.PaymentInitDto
import com.jatri.core.network.model.PaymentInitRequest
import com.jatri.core.network.model.PaymentStatusDto
import okhttp3.ResponseBody
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

    private val bus = MockBus()
    private val flight = MockFlight(bus)
    private val train = MockTrain(bus)

    override suspend fun searchTrains(body: TrainSearchRequest): ApiEnvelope<List<TrainTripDto>> {
        delay(LATENCY)
        return ApiEnvelope(true, train.search(body.fromId, body.toId, body.date, body.travelClass))
    }

    override suspend fun trainSeats(tripId: String, travelClass: String): ApiEnvelope<TrainSeatMapDto> {
        delay(LATENCY / 2)
        return ApiEnvelope(true, train.seatMap(tripId, travelClass))
    }

    override suspend fun createTrainBooking(body: TrainBookingRequest, idempotencyKey: String): ApiEnvelope<BookingDto> {
        delay(LATENCY)
        return ApiEnvelope(true, train.create(body, idempotencyKey, currentUser().phone))
    }

    override suspend fun searchFlights(body: FlightSearchRequest): ApiEnvelope<List<FlightOfferDto>> {
        delay(LATENCY * 2) // flight search is slower than bus search on real providers too
        return ApiEnvelope(true, flight.search(body))
    }

    override suspend fun flightOffer(offerId: String): ApiEnvelope<FlightOfferDto> {
        delay(LATENCY / 2)
        return ApiEnvelope(true, flight.offer(offerId))
    }

    override suspend fun createFlightBooking(body: FlightBookingRequest, idempotencyKey: String): ApiEnvelope<BookingDto> {
        delay(LATENCY)
        return ApiEnvelope(true, flight.create(body, idempotencyKey, currentUser().phone))
    }

    override suspend fun searchBuses(body: BusSearchRequest): ApiEnvelope<List<BusTripDto>> {
        delay(LATENCY)
        return ApiEnvelope(true, bus.search(body.fromId, body.toId, body.date, body.coachType))
    }

    override suspend fun busSeats(tripId: String): ApiEnvelope<SeatMapDto> {
        delay(LATENCY / 2)
        return ApiEnvelope(true, bus.seatMap(tripId))
    }

    override suspend fun createBooking(body: BookingCreateRequest, idempotencyKey: String): ApiEnvelope<BookingDto> {
        delay(LATENCY)
        return ApiEnvelope(true, bus.create(body, idempotencyKey, currentUser().phone))
    }

    override suspend fun bookings(): ApiEnvelope<List<BookingDto>> {
        currentUser()
        return ApiEnvelope(true, bus.list())
    }

    override suspend fun booking(id: String): ApiEnvelope<BookingDto> {
        currentUser()
        return ApiEnvelope(true, bus.get(id))
    }

    override suspend fun cancelBooking(id: String, body: CancelRequest): ApiEnvelope<BookingDto> {
        currentUser()
        delay(LATENCY / 2)
        return ApiEnvelope(true, bus.cancel(id, body.expectedRefund))
    }

    override suspend fun cancelQuote(id: String): ApiEnvelope<CancelQuoteDto> {
        currentUser()
        delay(LATENCY / 2)
        return ApiEnvelope(true, bus.quote(id))
    }

    override suspend fun notifications(limit: Int, before: String?): ApiEnvelope<NotificationPageDto> {
        currentUser()
        val items = bus.inbox.filter { before == null || it.createdAt < before }.take(limit)
        return ApiEnvelope(true, NotificationPageDto(items, bus.inbox.count { !it.isRead }))
    }

    override suspend fun markNotificationRead(id: String): ApiEnvelope<NotificationDto> {
        currentUser()
        val i = bus.inbox.indexOfFirst { it.id == id }
        if (i < 0) throw api("notification_not_found", "Notification not found.", "নোটিফিকেশন পাওয়া যায়নি।")
        bus.inbox[i] = bus.inbox[i].copy(isRead = true)
        return ApiEnvelope(true, bus.inbox[i])
    }

    override suspend fun markAllNotificationsRead(): ApiEnvelope<JsonElement?> {
        currentUser()
        bus.inbox.replaceAll { it.copy(isRead = true) }
        return ApiEnvelope(true, null)
    }

    /** No push in demo mode. */
    override suspend fun registerDevice(body: DeviceRequest): ApiEnvelope<JsonElement?> = ApiEnvelope(true, null)

    override suspend fun unregisterDevice(token: String): ApiEnvelope<JsonElement?> = ApiEnvelope(true, null)

    override suspend fun initPayment(body: PaymentInitRequest, idempotencyKey: String): ApiEnvelope<PaymentInitDto> {
        currentUser()
        delay(LATENCY)
        return ApiEnvelope(true, bus.initPayment(body.bookingId))
    }

    override suspend fun paymentStatus(id: String): ApiEnvelope<PaymentStatusDto> {
        currentUser()
        delay(LATENCY / 2)
        return ApiEnvelope(true, bus.paymentStatus(id))
    }

    /** The demo backend has no PDF renderer; the real server generates it. */
    override suspend fun ticketPdf(bookingId: String): ResponseBody =
        throw api("pdf_unavailable", "The PDF ticket needs the real server. Your QR ticket works.", "পিডিএফ টিকিটের জন্য আসল সার্ভার লাগবে। আপনার কিউআর টিকিট কাজ করবে।")

    /** Stands in for the gateway's server-to-server callback (demo gateway only). */
    suspend fun completeDemoPayment(paymentId: String, success: Boolean) {
        delay(LATENCY)
        bus.completePayment(paymentId, success)
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
