package com.jatri.core.data.seed

import com.jatri.core.model.CabinClass
import com.jatri.core.model.CoachType
import com.jatri.core.model.DiscountType
import com.jatri.core.model.FareBrand
import com.jatri.core.model.FareRules
import com.jatri.core.model.FlightJourney
import com.jatri.core.model.FlightLeg
import com.jatri.core.model.FlightOffer
import com.jatri.core.model.PassengerType
import com.jatri.core.model.PaxFare
import com.jatri.core.model.BusTrip
import com.jatri.core.model.Location
import com.jatri.core.model.Money
import com.jatri.core.model.Offer
import com.jatri.core.model.PopularRoute
import com.jatri.core.model.Seat
import com.jatri.core.model.SeatLayout
import com.jatri.core.model.SeatState
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TrainClassFare
import com.jatri.core.model.TrainTrip
import com.jatri.core.model.TravelMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * MOCK DATA — clearly fake content so screens can be built before real providers exist.
 * Operator and airline names here are generic placeholders, not real companies.
 */
object MockContent {
    private fun loc(id: String): Location = SeedLocations.byId.getValue(id)

    val popularRoutes: List<PopularRoute> = listOf(
        PopularRoute(TravelMode.BUS, loc("bus_dhaka"), loc("bus_coxsbazar"), Money.taka(1200)),
        PopularRoute(TravelMode.BUS, loc("bus_dhaka"), loc("bus_sylhet"), Money.taka(700)),
        PopularRoute(TravelMode.BUS, loc("bus_dhaka"), loc("bus_chattogram"), Money.taka(650)),
        PopularRoute(TravelMode.FLIGHT, loc("air_DAC"), loc("air_CXB"), Money.taka(5499)),
        PopularRoute(TravelMode.FLIGHT, loc("air_DAC"), loc("air_CGP"), Money.taka(4299)),
        PopularRoute(TravelMode.FLIGHT, loc("air_DAC"), loc("air_DXB"), Money.taka(38500)),
        PopularRoute(TravelMode.TRAIN, loc("stn_dhaka"), loc("stn_chattogram"), Money.taka(405)),
        PopularRoute(TravelMode.TRAIN, loc("stn_dhaka"), loc("stn_sylhet"), Money.taka(375)),
        PopularRoute(TravelMode.TRAIN, loc("stn_dhaka"), loc("stn_coxsbazar"), Money.taka(695)),
    )

    fun offers(today: LocalDate): List<Offer> = listOf(
        Offer(
            id = "o1", code = "JATRI150",
            titleEn = "৳150 off your first bus ticket", titleBn = "প্রথম বাস টিকিটে ৳১৫০ ছাড়",
            subtitleEn = "New users only", subtitleBn = "শুধু নতুন ব্যবহারকারীদের জন্য",
            discountType = DiscountType.FLAT, value = 150, modes = setOf(TravelMode.BUS),
            validTo = today.plusDays(30),
        ),
        Offer(
            id = "o2", code = "FLY8",
            titleEn = "8% off domestic flights", titleBn = "দেশের ভেতরে ফ্লাইটে ৮% ছাড়",
            subtitleEn = "Pay with bKash · up to ৳800", subtitleBn = "বিকাশে পেমেন্টে · সর্বোচ্চ ৳৮০০",
            discountType = DiscountType.PERCENT, value = 8, modes = setOf(TravelMode.FLIGHT),
            validTo = today.plusDays(14),
        ),
        Offer(
            id = "o3", code = "RAIL50",
            titleEn = "৳50 cashback on train tickets", titleBn = "ট্রেন টিকিটে ৳৫০ ক্যাশব্যাক",
            subtitleEn = "On Nagad payments", subtitleBn = "নগদে পেমেন্ট করলে",
            discountType = DiscountType.FLAT, value = 50, modes = setOf(TravelMode.TRAIN),
            validTo = today.plusDays(7),
        ),
    )

    /** Sample offers for previews and screenshot tests (the app itself gets offers from the API). */
    fun flightOffers(date: LocalDate, from: Location, to: Location): List<FlightOffer> = listOf(
        flight("f1", "S1", "Sample Air", "S1 101", date, 7, 30, 65, FareBrand.SAVER, 5499, from, to),
        flight("f2", "D2", "Demo Airways", "D2 433", date, 10, 15, 60, FareBrand.FLEX, 6420, from, to),
        flight("f3", "T3", "Test Wings", "T3 207", date, 14, 45, 70, FareBrand.SAVER, 5150, from, to),
        flight("f4", "M4", "Mock Air", "M4 115", date, 19, 0, 65, FareBrand.FLEX, 6199, from, to),
    )

    private fun flight(
        id: String, code: String, airline: String, number: String, date: LocalDate, h: Int, m: Int, mins: Long,
        brand: FareBrand, taka: Long, from: Location, to: Location,
    ): FlightOffer {
        val dep = LocalDateTime.of(date, LocalTime.of(h, m))
        val duration = java.time.Duration.ofMinutes(mins)
        val leg = FlightLeg(code, airline, number, "ATR 72-600", from, to, dep, dep.plus(duration), duration)
        val flex = brand == FareBrand.FLEX
        val tax = Money.taka(1000)
        val base = Money.taka(taka) - tax - Money.taka(200)
        return FlightOffer(
            id = id,
            journeys = listOf(FlightJourney(from, to, dep, dep.plus(duration), duration, listOf(leg))),
            cabinClass = CabinClass.ECONOMY,
            fareBrand = brand,
            rules = FareRules(
                refundable = flex,
                refundFee = if (flex) Money.taka(1500) else null,
                changeFee = Money.taka(if (flex) 1000 else 2500),
                deadlineHours = 3,
                checkedBaggageKg = 20,
                cabinBaggageKg = 7,
            ),
            fares = listOf(PaxFare(PassengerType.ADULT, 1, base, tax)),
            baseTotal = base,
            taxTotal = tax,
            serviceCharge = Money.taka(200),
            price = Money.taka(taka),
            seatsLeft = 4 + taka.toInt() % 5,
            international = false,
        )
    }

    fun busTrips(date: LocalDate, from: Location, to: Location): List<BusTrip> = listOf(
        bus("b1", "Sample Paribahan", "GL-12", CoachType.AC, date, 8, 0, 600, "Arambagh", 18, 1400, from, to),
        bus("b2", "Demo Travels", "SH-07", CoachType.NON_AC, date, 9, 30, 660, "Kalabagan", 3, 900, from, to),
        bus("b3", "Test Express", "HN-21", CoachType.AC, date, 22, 0, 600, "Sayedabad", 26, 1300, from, to),
        bus("b4", "Night Coach Demo", "NC-03", CoachType.SLEEPER, date, 23, 0, 630, "Gabtoli", 9, 2200, from, to),
    )

    private fun bus(
        id: String, op: String, coach: String, type: CoachType, date: LocalDate, h: Int, m: Int, mins: Long,
        boarding: String, seats: Int, taka: Long, from: Location, to: Location,
    ): BusTrip {
        val dep = LocalDateTime.of(date, LocalTime.of(h, m))
        return BusTrip(id, op, null, coach, type, from, to, dep, dep.plusMinutes(mins), boarding, seats, Money.taka(taka))
    }

    fun trainTrips(date: LocalDate, from: Location, to: Location): List<TrainTrip> = listOf(
        TrainTrip(
            "t1", "Sample Express", "701", from, to,
            LocalDateTime.of(date, LocalTime.of(7, 0)), LocalDateTime.of(date, LocalTime.of(12, 30)),
            DayOfWeek.TUESDAY,
            listOf(
                TrainClassFare(TrainClass.SHOVAN_CHAIR, 42, Money.taka(405)),
                TrainClassFare(TrainClass.SNIGDHA, 6, Money.taka(776)),
                TrainClassFare(TrainClass.AC_BERTH, 0, Money.taka(1398)),
            ),
        ),
        TrainTrip(
            "t2", "Demo Intercity", "787", from, to,
            LocalDateTime.of(date, LocalTime.of(15, 0)), LocalDateTime.of(date, LocalTime.of(21, 10)),
            null,
            listOf(
                TrainClassFare(TrainClass.SHOVAN, 80, Money.taka(345)),
                TrainClassFare(TrainClass.SHOVAN_CHAIR, 15, Money.taka(405)),
                TrainClassFare(TrainClass.AC_SEAT, 4, Money.taka(926)),
            ),
        ),
    )

    /** A 2+2 coach with 10 rows (A–J) plus some booked and ladies seats. */
    fun busSeatLayout(): SeatLayout {
        val booked = setOf("A1", "A2", "B3", "C4", "E1", "E2", "G3", "H4")
        val ladies = setOf("D1", "D2")
        val rows = ('A'..'J').map { row ->
            val seats = (1..4).map { n ->
                val number = "$row$n"
                val state = when (number) {
                    in booked -> SeatState.BOOKED
                    in ladies -> SeatState.LADIES
                    else -> SeatState.AVAILABLE
                }
                Seat(number, state)
            }
            listOf(seats[0], seats[1], null, seats[2], seats[3])
        }
        return SeatLayout(rows)
    }
}
