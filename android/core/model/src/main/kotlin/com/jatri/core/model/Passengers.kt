package com.jatri.core.model

/** Passenger counts for a search. Infants (under 2) travel on an adult's lap on flights. */
data class PassengerCount(
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
) {
    val total: Int get() = adults + children + infants

    companion object {
        const val MAX_TOTAL = 9
    }
}

enum class CabinClass { ECONOMY, PREMIUM_ECONOMY, BUSINESS }

enum class CoachType { ANY, AC, NON_AC, SLEEPER }

/** Bangladesh Railway seat classes. */
enum class TrainClass { SHOVAN, SHOVAN_CHAIR, SNIGDHA, AC_SEAT, AC_BERTH, F_BERTH, F_SEAT }

enum class PassengerType { ADULT, CHILD, INFANT }

enum class Gender { MALE, FEMALE, OTHER }
