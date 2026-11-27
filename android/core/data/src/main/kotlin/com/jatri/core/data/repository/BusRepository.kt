package com.jatri.core.data.repository

import com.jatri.core.model.AppError
import com.jatri.core.model.AppException
import com.jatri.core.model.Booking
import com.jatri.core.model.CancelQuote
import com.jatri.core.model.Refund
import com.jatri.core.model.RefundStatus
import com.jatri.core.network.model.CancelRequest
import com.jatri.core.model.BookingPassenger
import com.jatri.core.model.BookingSegment
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.BusHoldRequest
import com.jatri.core.model.BusSeatMap
import com.jatri.core.model.BusTrip
import com.jatri.core.model.CoachType
import com.jatri.core.model.Gender
import com.jatri.core.model.Location
import com.jatri.core.model.Money
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatLayout
import com.jatri.core.model.SeatState
import com.jatri.core.model.StopPoint
import com.jatri.core.model.TravelMode
import com.jatri.core.model.Ticket
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.apiCall
import com.jatri.core.network.model.BookingCreateRequest
import com.jatri.core.network.model.BookingDto
import com.jatri.core.network.model.BusSearchRequest
import com.jatri.core.network.model.BusTripDto
import com.jatri.core.network.model.PassengerInDto
import com.jatri.core.network.model.StopPointDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

interface BusRepository {
    suspend fun search(from: Location, to: Location, date: LocalDate, coachType: CoachType): List<BusTrip>
    suspend fun seatMap(tripId: String): BusSeatMap
}

/** Bookings across modes. Lists and tickets are cached on the phone so they open offline. */
interface BookingRepository {
    suspend fun createBusHold(request: BusHoldRequest): Booking

    /** From the server; falls back to the phone's copy when offline (then throws only if never cached). */
    suspend fun get(id: String): Booking
    suspend fun list(): List<Booking>

    /** The bookings last loaded, for offline use (empty if none). */
    suspend fun cached(): List<Booking>

    suspend fun cancelQuote(id: String): CancelQuote

    /** Releases a hold, or cancels a ticket by its quote ([expectedRefund] = the quote's refund). */
    suspend fun cancel(id: String, expectedRefund: Money? = null): Booking
}

@Singleton
internal class NetworkBusRepository @Inject constructor(
    private val api: JatriApi,
    private val locations: LocationRepository,
) : BusRepository {
    override suspend fun search(from: Location, to: Location, date: LocalDate, coachType: CoachType): List<BusTrip> {
        val coach = when (coachType) {
            CoachType.ANY -> null
            CoachType.AC -> "ac"
            CoachType.NON_AC -> "non_ac"
            CoachType.SLEEPER -> "sleeper"
        }
        return apiCall { api.searchBuses(BusSearchRequest(from.id, to.id, date.toString(), coach)) }.map { it.toModel(from, to) }
    }

    override suspend fun seatMap(tripId: String): BusSeatMap {
        val dto = apiCall { api.busSeats(tripId) }
        val from = locations.getById(dto.trip.fromId) ?: unknownLocation(dto.trip.fromId)
        val to = locations.getById(dto.trip.toId) ?: unknownLocation(dto.trip.toId)
        val rows = dto.rows.map { row ->
            row.map { s ->
                s?.let {
                    Seat(
                        it.number,
                        when (it.state) {
                            "booked" -> SeatState.BOOKED
                            "ladies" -> SeatState.LADIES
                            "held" -> SeatState.HELD
                            else -> SeatState.AVAILABLE
                        },
                    )
                }
            }
        }
        return BusSeatMap(dto.trip.toModel(from, to), SeatLayout(rows), dto.maxSelectable, Money(dto.serviceChargePerSeat))
    }

    private fun unknownLocation(id: String) =
        Location(id, com.jatri.core.model.LocationType.BUS_CITY, id, id, id, id, id)
}

@Singleton
internal class NetworkBookingRepository @Inject constructor(
    private val api: JatriApi,
    private val cache: BookingCache,
) : BookingRepository {
    override suspend fun createBusHold(request: BusHoldRequest): Booking {
        val body = BookingCreateRequest(
            tripId = request.tripId,
            seats = request.seats,
            boardingPointId = request.boardingPointId,
            droppingPointId = request.droppingPointId,
            passengers = request.passengers.map {
                PassengerInDto(it.fullName.trim(), it.gender.name.lowercase(), it.age, it.seatNo, it.nid?.takeIf(String::isNotBlank))
            },
            contactPhone = request.contactPhone,
            contactEmail = request.contactEmail?.takeIf(String::isNotBlank),
            expectedTotal = request.expectedTotal.paisa,
        )
        return apiCall { api.createBooking(body, request.idempotencyKey) }.toModel()
    }

    override suspend fun get(id: String): Booking = try {
        apiCall { api.booking(id) }.also { cache.put(it) }.toModel()
    } catch (e: AppException) {
        if (e.error !is AppError.Network) throw e
        cache.get(id)?.toModel() ?: throw e
    }

    override suspend fun list(): List<Booking> = apiCall { api.bookings() }.also { cache.replaceAll(it) }.map { it.toModel() }

    override suspend fun cached(): List<Booking> = cache.all().map { it.toModel() }

    override suspend fun cancelQuote(id: String): CancelQuote = apiCall { api.cancelQuote(id) }.let {
        CancelQuote(
            cancellable = it.cancellable, paid = Money(it.paid), refund = Money(it.refund), fee = Money(it.fee),
            nonRefundable = Money(it.nonRefundable), reason = it.reason, messageEn = it.messageEn, messageBn = it.messageBn,
            deadline = it.deadline?.let(::parseInstant),
        )
    }

    override suspend fun cancel(id: String, expectedRefund: Money?): Booking =
        apiCall { api.cancelBooking(id, CancelRequest(expectedRefund?.paisa)) }.also { cache.put(it) }.toModel()
}

// ---- mapping -----------------------------------------------------------------------------

private val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")

/** Server times are ISO-8601 with an offset; show them in Bangladesh time. */
internal fun parseLocal(value: String): LocalDateTime = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(DHAKA).toLocalDateTime()
}.getOrElse { LocalDateTime.parse(value).atOffset(ZoneOffset.UTC).atZoneSameInstant(DHAKA).toLocalDateTime() }

/** The wall-clock time written in the string (an airport's local time), ignoring its offset. */
internal fun parseWallTime(value: String): LocalDateTime =
    runCatching { OffsetDateTime.parse(value).toLocalDateTime() }.getOrElse { LocalDateTime.parse(value) }

internal fun parseInstant(value: String): Instant = runCatching { OffsetDateTime.parse(value).toInstant() }
    .getOrElse { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) } // naive = UTC

private fun StopPointDto.toModel() = StopPoint(id, nameEn, nameBn, parseLocal(time))

internal fun BusTripDto.toModel(from: Location, to: Location) = BusTrip(
    id = id,
    operatorName = operatorName,
    operatorLogoUrl = operatorLogoUrl,
    coachNumber = coachNumber,
    coachType = when (coachType) {
        "ac" -> CoachType.AC
        "sleeper" -> CoachType.SLEEPER
        else -> CoachType.NON_AC
    },
    from = from,
    to = to,
    departAt = parseLocal(departAt),
    arriveAt = parseLocal(arriveAt),
    boardingPoint = boardingPoints.firstOrNull()?.nameEn.orEmpty(),
    seatsAvailable = seatsAvailable,
    price = Money(fare),
    boardingPoints = boardingPoints.map { it.toModel() },
    droppingPoints = droppingPoints.map { it.toModel() },
)

internal fun BookingDto.toModel() = Booking(
    id = id,
    ref = bookingRef,
    mode = runCatching { TravelMode.valueOf(mode.uppercase()) }.getOrDefault(TravelMode.BUS),
    status = runCatching { BookingStatus.valueOf(status.uppercase()) }.getOrDefault(BookingStatus.PENDING),
    holdExpiresAt = holdExpiresAt?.let(::parseInstant),
    baseFare = Money(baseFare),
    serviceCharge = Money(serviceCharge),
    discount = Money(discount),
    total = Money(totalAmount),
    contactPhone = contactPhone,
    contactEmail = contactEmail,
    segments = segments.map {
        BookingSegment(
            carrier = it.carrier,
            number = it.number,
            fromId = it.fromId,
            toId = it.toId,
            // Flights keep the airport's own clock (as on the boarding pass); bus/train use Dhaka time.
            departAt = if (it.fromCode != null) parseWallTime(it.departAt) else parseLocal(it.departAt),
            arriveAt = if (it.fromCode != null) parseWallTime(it.arriveAt) else parseLocal(it.arriveAt),
            travelClass = it.travelClass,
            seatNumbers = it.seatNumbers,
            boardingPoint = it.boardingPoint?.toModel(),
            droppingPoint = it.droppingPoint?.toModel(),
            coach = it.coach,
            journeyIndex = it.sliceIndex ?: 0,
            aircraft = it.aircraft,
            baggage = it.baggage,
        )
    },
    passengers = passengers.map {
        BookingPassenger(
            fullName = it.fullName,
            gender = runCatching { Gender.valueOf(it.gender.uppercase()) }.getOrDefault(Gender.OTHER),
            age = it.age,
            seatNo = it.seatNo,
            documentMasked = it.documentMasked,
            ticketNumber = it.ticketNumber,
            type = passengerType(it.type),
        )
    },
    ticket = ticket?.let { Ticket(it.qrPayload, it.pdfUrl, parseInstant(it.issuedAt)) },
    taxes = Money(taxes),
    cancelledAt = cancelledAt?.let(::parseInstant),
    refund = refund?.let {
        Refund(
            amount = Money(it.amount),
            fee = Money(it.fee),
            status = runCatching { RefundStatus.valueOf(it.status.uppercase()) }.getOrDefault(RefundStatus.PENDING),
            processedAt = it.processedAt?.let(::parseInstant),
        )
    },
    cabinClass = cabin?.let(::cabinClass),
    fareBrand = fareBrand?.let(::fareBrand),
    fareRules = fareRules?.toModel(),
)
