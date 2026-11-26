package com.jatri.core.common.format

import com.jatri.core.model.AppLanguage
import com.jatri.core.model.Money

/**
 * Formats BDT amounts the way people read them in Bangladesh:
 * `৳ 1,250`, `৳ 1,25,000` (lakh grouping), or `৳ ১,২৫০` in Bangla.
 * Paisa are shown only when they are not zero (`৳ 99.50`).
 */
object PriceFormatter {
    const val TAKA_SIGN = "৳"

    fun format(money: Money, language: AppLanguage): String {
        val negative = money.paisa < 0
        val abs = if (negative) -money.paisa else money.paisa
        val taka = groupSouthAsian(abs / 100)
        val paisa = abs % 100
        val number = if (paisa == 0L) taka else "$taka.${paisa.toString().padStart(2, '0')}"
        return "${if (negative) "-" else ""}$TAKA_SIGN $number".localizeDigits(language)
    }

    /** 1234567 -> "12,34,567": last three digits, then groups of two. */
    internal fun groupSouthAsian(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val groups = rest.reversed().chunked(2).map { it.reversed() }.reversed()
        return groups.joinToString(",") + "," + lastThree
    }
}

fun Money.format(language: AppLanguage): String = PriceFormatter.format(this, language)
