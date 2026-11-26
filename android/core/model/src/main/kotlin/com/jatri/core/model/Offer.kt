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
    val modes: Set<TravelMode>,
    val validTo: LocalDate,
) {
    fun title(language: AppLanguage) = if (language == AppLanguage.BANGLA) titleBn else titleEn
    fun subtitle(language: AppLanguage) = if (language == AppLanguage.BANGLA) subtitleBn else subtitleEn
}
