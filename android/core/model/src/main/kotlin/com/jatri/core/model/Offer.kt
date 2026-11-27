package com.jatri.core.model

import java.time.LocalDate

enum class DiscountType { FLAT, PERCENT }

data class Offer(
    val id: String,
    val code: String,
    val titleEn: String,
    val titleBn: String,
    val subtitleEn: String,
    val subtitleBn: String,
    val discountType: DiscountType,
    val value: Long,
    /** Empty = every mode. */
    val modes: Set<TravelMode>,
    val validTo: LocalDate,
    val maxDiscount: Money? = null,
    val minAmount: Money = Money.ZERO,
    /** Payment methods the code needs; empty = any. */
    val methods: Set<PaymentMethod> = emptySet(),
    val firstBookingOnly: Boolean = false,
) {
    fun title(language: AppLanguage) = if (language == AppLanguage.BANGLA) titleBn else titleEn
    fun subtitle(language: AppLanguage) = if (language == AppLanguage.BANGLA) subtitleBn else subtitleEn
}

/** A family member or friend saved for one-tap passenger forms. */
data class SavedTraveller(
    val id: String?,
    /** As on the NID (bus, train). */
    val fullName: String,
    /** As on the passport (flights), English letters. */
    val givenName: String? = null,
    val surname: String? = null,
    val gender: Gender,
    val dateOfBirth: LocalDate? = null,
    val nationality: String = "BD",
    val nid: String? = null,
    val passportNo: String? = null,
    val passportExpiry: LocalDate? = null,
) {
    fun ageOn(day: LocalDate): Int? = dateOfBirth?.let { java.time.Period.between(it, day).years }
}

/** "Tell me when this route on this day drops to [target]." */
data class PriceAlert(
    val id: String,
    val mode: TravelMode,
    val fromId: String,
    val toId: String,
    val date: LocalDate,
    val target: Money,
    /** Lowest fare per person at the last check (null: nothing on sale yet). */
    val lastPrice: Money?,
    val isActive: Boolean,
)
