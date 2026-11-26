package com.jatri.core.model

/**
 * Money is always stored as whole **paisa** (1 BDT = 100 paisa) — never as a floating-point number.
 */
@JvmInline
value class Money(val paisa: Long) : Comparable<Money> {
    val taka: Long get() = paisa / 100
    val paisaPart: Long get() = paisa % 100

    operator fun plus(other: Money) = Money(paisa + other.paisa)
    operator fun minus(other: Money) = Money(paisa - other.paisa)
    operator fun times(count: Int) = Money(paisa * count)
    override fun compareTo(other: Money): Int = paisa.compareTo(other.paisa)

    companion object {
        val ZERO = Money(0)
        fun taka(amount: Long) = Money(amount * 100)
    }
}
