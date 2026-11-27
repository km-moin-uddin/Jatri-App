package com.jatri.core.common.format

import com.jatri.core.model.AppLanguage

private const val BANGLA_ZERO = '০'

/** Replaces ASCII digits 0-9 with Bangla digits ০-৯. Other characters are left alone. */
fun String.toBanglaDigits(): String = buildString(length) {
    for (ch in this@toBanglaDigits) append(if (ch in '0'..'9') BANGLA_ZERO + (ch - '0') else ch)
}

/** Uses Bangla digits when the app language is Bangla, ASCII digits otherwise. */
fun String.localizeDigits(language: AppLanguage): String =
    if (language == AppLanguage.BANGLA) toBanglaDigits() else this

fun Number.localized(language: AppLanguage): String = toString().localizeDigits(language)

private val CODE_TOKEN = Regex("[A-Za-z0-9]*[0-9][A-Za-z0-9]*")

/**
 * Like [localizeDigits], but codes that mix letters and digits stay exactly as printed on the
 * ticket ("D2 433" → "D2 ৪৩৩", "PNR-783E0218-1" keeps "783E0218").
 */
fun String.localizeKeepingCodes(language: AppLanguage): String =
    if (language != AppLanguage.BANGLA) this else CODE_TOKEN.replace(this) { m -> if (m.value.any { it.isLetter() }) m.value else m.value.toBanglaDigits() }
