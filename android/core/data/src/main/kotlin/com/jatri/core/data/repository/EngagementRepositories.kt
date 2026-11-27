package com.jatri.core.data.repository

import com.jatri.core.data.seed.MockContent
import com.jatri.core.model.DiscountType
import com.jatri.core.model.Gender
import com.jatri.core.model.Money
import com.jatri.core.model.Offer
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.PriceAlert
import com.jatri.core.model.SavedTraveller
import com.jatri.core.model.TravelMode
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.apiCall
import com.jatri.core.network.model.OfferDto
import com.jatri.core.network.model.PriceAlertDto
import com.jatri.core.network.model.PriceAlertRequest
import com.jatri.core.network.model.TravellerDto
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface OfferRepository {
    /** Running offers; the bundled samples when the server can't be reached. */
    suspend fun offers(): List<Offer>
}

interface TravellerRepository {
    suspend fun list(): List<SavedTraveller>

    /** Adds ([SavedTraveller.id] null) or updates. */
    suspend fun save(traveller: SavedTraveller): SavedTraveller
    suspend fun delete(id: String)
}

interface PriceAlertRepository {
    suspend fun list(): List<PriceAlert>
    suspend fun create(mode: TravelMode, fromId: String, toId: String, date: LocalDate, target: Money): PriceAlert
    suspend fun delete(id: String)
}

@Singleton
internal class NetworkOfferRepository @Inject constructor(private val api: JatriApi, private val clock: Clock) : OfferRepository {
    override suspend fun offers(): List<Offer> =
        runCatching { apiCall { api.offers() }.map { it.toModel() } }.getOrElse { MockContent.offers(LocalDate.now(clock)) }
}

@Singleton
internal class NetworkTravellerRepository @Inject constructor(private val api: JatriApi) : TravellerRepository {
    override suspend fun list() = apiCall { api.travellers() }.map { it.toModel() }

    override suspend fun save(traveller: SavedTraveller): SavedTraveller {
        val dto = traveller.toDto()
        return (traveller.id?.let { id -> apiCall { api.editTraveller(id, dto) } } ?: apiCall { api.addTraveller(dto) }).toModel()
    }

    override suspend fun delete(id: String) {
        apiCall { api.deleteTraveller(id) }
    }
}

@Singleton
internal class NetworkPriceAlertRepository @Inject constructor(private val api: JatriApi) : PriceAlertRepository {
    override suspend fun list() = apiCall { api.priceAlerts() }.map { it.toModel() }

    override suspend fun create(mode: TravelMode, fromId: String, toId: String, date: LocalDate, target: Money) =
        apiCall { api.addPriceAlert(PriceAlertRequest(mode.name.lowercase(), fromId, toId, date.toString(), target.paisa)) }.toModel()

    override suspend fun delete(id: String) {
        apiCall { api.deletePriceAlert(id) }
    }
}

// ---- mapping -----------------------------------------------------------------------------

private fun mode(value: String) = runCatching { TravelMode.valueOf(value.uppercase()) }.getOrNull()

internal fun OfferDto.toModel() = Offer(
    id = id,
    code = code,
    titleEn = titleEn,
    titleBn = titleBn,
    subtitleEn = subtitleEn,
    subtitleBn = subtitleBn,
    discountType = if (discountType == "percent") DiscountType.PERCENT else DiscountType.FLAT,
    // The UI shows flat offers in taka ("৳150 off") and percent offers as a whole percent.
    value = if (discountType == "percent") value else value / 100,
    modes = modes.mapNotNull(::mode).toSet(),
    validTo = LocalDate.parse(validTo),
    maxDiscount = maxDiscount?.let(::Money),
    minAmount = Money(minAmount),
    methods = methods.mapNotNull { m -> runCatching { PaymentMethod.valueOf(m.uppercase()) }.getOrNull() }.toSet(),
    firstBookingOnly = firstBookingOnly,
)

internal fun TravellerDto.toModel() = SavedTraveller(
    id = id,
    fullName = fullName,
    givenName = givenName,
    surname = surname,
    gender = runCatching { Gender.valueOf(gender.uppercase()) }.getOrDefault(Gender.OTHER),
    dateOfBirth = dateOfBirth?.let(LocalDate::parse),
    nationality = nationality,
    nid = nidNumber,
    passportNo = passportNo,
    passportExpiry = passportExpiry?.let(LocalDate::parse),
)

internal fun SavedTraveller.toDto() = TravellerDto(
    fullName = fullName.trim(),
    givenName = givenName?.trim()?.takeIf(String::isNotBlank),
    surname = surname?.trim()?.takeIf(String::isNotBlank),
    gender = gender.name.lowercase(),
    dateOfBirth = dateOfBirth?.toString(),
    nationality = nationality,
    nidNumber = nid?.takeIf(String::isNotBlank),
    passportNo = passportNo?.trim()?.uppercase()?.takeIf(String::isNotBlank),
    passportExpiry = passportExpiry?.toString(),
)

internal fun PriceAlertDto.toModel() = PriceAlert(
    id = id,
    mode = mode(mode) ?: TravelMode.BUS,
    fromId = fromId,
    toId = toId,
    date = LocalDate.parse(date),
    target = Money(targetPrice),
    lastPrice = lastPrice?.let(::Money),
    isActive = isActive,
)
