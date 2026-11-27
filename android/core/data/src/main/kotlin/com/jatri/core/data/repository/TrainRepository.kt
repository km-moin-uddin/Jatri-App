package com.jatri.core.data.repository

import com.jatri.core.model.Booking
import com.jatri.core.model.Location
import com.jatri.core.model.LocationType
import com.jatri.core.model.Money
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatLayout
import com.jatri.core.model.SeatState
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TrainClassFare
import com.jatri.core.model.TrainCoach
import com.jatri.core.model.TrainHoldRequest
import com.jatri.core.model.TrainSeatMap
import com.jatri.core.model.TrainTrip
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.apiCall
import com.jatri.core.network.model.TrainBookingRequest
import com.jatri.core.network.model.TrainPassengerInDto
import com.jatri.core.network.model.TrainSearchRequest
import com.jatri.core.network.model.TrainTripDto
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface TrainRepository {
    /** Trains on [date] (up to 10 days ahead, `outside_sale_window` otherwise). [trainClass] null = all classes. */
    suspend fun search(from: Location, to: Location, date: LocalDate, trainClass: TrainClass?): List<TrainTrip>

    suspend fun seatMap(tripId: String, trainClass: TrainClass): TrainSeatMap

    suspend fun hold(request: TrainHoldRequest): Booking
}

@Singleton
internal class NetworkTrainRepository @Inject constructor(
    private val api: JatriApi,
    private val locations: LocationRepository,
) : TrainRepository {
    override suspend fun search(from: Location, to: Location, date: LocalDate, trainClass: TrainClass?): List<TrainTrip> =
        apiCall { api.searchTrains(TrainSearchRequest(from.id, to.id, date.toString(), trainClass?.code)) }.map { it.toModel(from, to) }

    override suspend fun seatMap(tripId: String, trainClass: TrainClass): TrainSeatMap {
        val dto = apiCall { api.trainSeats(tripId, trainClass.code) }
        val from = locations.getById(dto.trip.fromId) ?: station(dto.trip.fromId)
        val to = locations.getById(dto.trip.toId) ?: station(dto.trip.toId)
        return TrainSeatMap(
            trip = dto.trip.toModel(from, to),
            trainClass = trainClassOf(dto.travelClass) ?: trainClass,
            fare = Money(dto.fare),
            coaches = dto.coaches.map { c ->
                TrainCoach(
                    name = c.name,
                    berth = c.layout == "berth",
                    layout = SeatLayout(
                        c.rows.map { row ->
                            row.map { s ->
                                s?.let {
                                    Seat(
                                        it.number,
                                        when (it.state) {
                                            "booked" -> SeatState.BOOKED
                                            "held" -> SeatState.HELD
                                            else -> SeatState.AVAILABLE
                                        },
                                    )
                                }
                            }
                        },
                    ),
                )
            },
            maxSelectable = dto.maxSelectable,
            serviceChargePerSeat = Money(dto.serviceChargePerSeat),
        )
    }

    override suspend fun hold(request: TrainHoldRequest): Booking {
        val body = TrainBookingRequest(
            mode = "train",
            tripId = request.tripId,
            travelClass = request.trainClass.code,
            seats = request.seats,
            passengers = request.passengers.map {
                TrainPassengerInDto(it.fullName.trim(), it.gender.name.lowercase(), requireNotNull(it.age), it.seatNo, it.nid?.takeIf(String::isNotBlank))
            },
            contactPhone = request.contactPhone,
            contactEmail = request.contactEmail?.takeIf(String::isNotBlank),
            expectedTotal = request.expectedTotal.paisa,
        )
        return apiCall { api.createTrainBooking(body, request.idempotencyKey) }.toModel()
    }

    private fun station(id: String) = Location(id, LocationType.STATION, id, id, id, id, id)
}

/** The API's class code: SHOVAN_CHAIR ↔ "shovan_chair". */
val TrainClass.code: String get() = name.lowercase()

fun trainClassOf(code: String): TrainClass? = TrainClass.entries.firstOrNull { it.code == code }

internal fun TrainTripDto.toModel(from: Location, to: Location) = TrainTrip(
    id = id,
    trainName = trainName,
    trainNumber = trainNumber,
    from = from,
    to = to,
    departAt = parseLocal(departAt),
    arriveAt = parseLocal(arriveAt),
    offDay = offDay?.let { runCatching { DayOfWeek.valueOf(it.uppercase()) }.getOrNull() },
    classes = classes.mapNotNull { c -> trainClassOf(c.travelClass)?.let { TrainClassFare(it, c.seatsAvailable, Money(c.fare)) } },
)
