package com.jatri.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.jatri.app.ui.JatriBottomBar
import com.jatri.app.ui.TopLevelDestination
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jatri.core.data.seed.MockContent
import com.jatri.core.designsystem.component.EmptyState
import com.jatri.core.designsystem.component.ErrorState
import com.jatri.core.designsystem.component.PriceSummaryBar
import com.jatri.core.designsystem.component.ResultCardSkeleton
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.PassengerCount
import com.jatri.core.model.SearchQuery
import com.jatri.core.model.TravelMode
import com.jatri.core.model.UserSettings
import com.jatri.core.ui.result.BusResultCard
import com.jatri.core.ui.result.FlightResultCard
import com.jatri.core.ui.result.ResultSort
import com.jatri.core.ui.result.ResultsScaffold
import com.jatri.core.ui.result.TrainResultCard
import com.jatri.core.ui.seat.SeatMap
import com.jatri.feature.account.AccountScreen
import com.jatri.feature.auth.FormState
import com.jatri.feature.auth.LoginScreen
import com.jatri.feature.auth.OtpScreen
import com.jatri.core.model.AppError
import com.jatri.core.model.SessionState
import com.jatri.core.model.User
import com.jatri.feature.auth.OnboardingScreen
import com.jatri.feature.home.HomeActions
import com.jatri.feature.home.HomeScreen
import com.jatri.feature.home.HomeUiState
import com.jatri.feature.offers.OffersScreen
import com.jatri.feature.payment.PaymentResultScreen
import com.jatri.feature.payment.ReviewPayScreen
import com.jatri.feature.payment.TicketResultScreen
import com.jatri.core.model.Ticket
import com.jatri.feature.trips.TripsScreen
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.Booking
import com.jatri.core.model.BookingPassenger
import com.jatri.core.model.BookingSegment
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Gender
import com.jatri.core.model.Money
import com.jatri.core.model.StopPoint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * Renders key screens to PNG on the JVM (no emulator). Run:
 *   ./gradlew :app:recordRoborazziDebug
 * Images land in app/build/outputs/roborazzi/. Subclasses set language and theme.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest(private val name: String, private val dark: Boolean) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 26)
    private val dhakaCox = MockContent.popularRoutes.first { it.mode == TravelMode.BUS }

    private fun shot(screen: String, content: @Composable () -> Unit) {
        compose.setContent {
            JatriTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$screen.png")
    }

    @Test fun home() = shot("01_home") {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { JatriBottomBar(current = TopLevelDestination.HOME, onSelect = {}) },
        ) { padding ->
            HomeScreen(
                modifier = Modifier.padding(padding),
            state = HomeUiState(
                query = SearchQuery(TravelMode.BUS, dhakaCox.from, dhakaCox.to, today.plusDays(1), passengers = PassengerCount(2)),
                today = today,
                popularRoutes = MockContent.popularRoutes.filter { it.mode == TravelMode.BUS },
                offers = MockContent.offers(today),
            ),
            actions = HomeActions(),
            now = LocalTime.of(9, 0),
            )
        }
    }

    @Test fun onboarding() = shot("00_onboarding") {
        OnboardingScreen(language = if (name.startsWith("bn")) AppLanguage.BANGLA else AppLanguage.ENGLISH, onLanguageChange = {}, onFinish = {})
    }

    @Test fun login() = shot("02_login") {
        LoginScreen(state = FormState(), onBack = {}, onSubmit = {}, onPhoneEdited = {}, onContinueAsGuest = {})
    }

    @Test fun otpError() = shot("02b_otp_error") {
        OtpScreen(
            phone = "01712345678",
            code = "000000",
            state = FormState(error = AppError.Api("otp_invalid", "The code is not correct.", "কোডটি সঠিক নয়।")),
            secondsLeft = 42,
            showDemoHint = true,
            onCodeChange = {}, onVerify = {}, onResend = {}, onBack = {},
        )
    }

    @Test fun accountLoggedIn() = shot("10b_account_logged_in") {
        AccountScreen(
            UserSettings(), {}, {}, {}, {}, {}, {}, {},
            session = SessionState.LoggedIn(User("u1", "01712345678", "Rahim Uddin", null, AppLanguage.BANGLA)),
        )
    }

    @Test fun busResults() = shot("03_bus_results") {
        ResultsScaffold(
            title = "${dhakaCox.from.city(JatriTheme.language)} → ${dhakaCox.to.city(JatriTheme.language)}",
            subtitle = "",
            loading = false,
            count = 4,
            sort = ResultSort.EARLIEST,
            onSortChange = {},
            onBack = {},
            phase = "Phase 3",
            date = today.plusDays(1),
            onDateChange = {},
            today = today,
        ) {
            items(MockContent.busTrips(today, dhakaCox.from, dhakaCox.to)) { BusResultCard(it, onClick = {}) }
        }
    }

    @Test fun flightAndTrainCards() = shot("04_flight_train_cards") {
        val air = MockContent.popularRoutes.first { it.mode == TravelMode.FLIGHT }
        val rail = MockContent.popularRoutes.first { it.mode == TravelMode.TRAIN }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MockContent.flightOffers(today, air.from, air.to).take(2).forEach { FlightResultCard(it, onClick = {}) }
            TrainResultCard(MockContent.trainTrips(today, rail.from, rail.to).first(), onClassClick = {})
            ResultCardSkeleton()
        }
    }

    @Test fun seatMap() = shot("05_seat_map") {
        Column(Modifier.fillMaxSize()) {
            SeatMap(
                layout = MockContent.busSeatLayout(),
                selected = setOf("F1", "F2"),
                onSeatToggle = {},
                modifier = Modifier.weight(1f).padding(Spacing.screen),
            )
            PriceSummaryBar(label = "F1, F2", price = "৳ 2,800", buttonText = "Continue", onClick = {})
        }
    }

    @Test fun reviewPay() = shot("06_review_pay") {
        val trip = MockContent.busTrips(today.plusDays(1), dhakaCox.from, dhakaCox.to).first()
        val point = StopPoint("p", "Kalabagan", "কলাবাগান", trip.departAt)
        ReviewPayScreen(
            onBack = {},
            onPaid = {},
            secondsLeft = 9 * 60 + 12,
            onCancel = {},
            booking = Booking(
                id = "b1", ref = "JTW4US", mode = TravelMode.BUS, status = BookingStatus.HELD, holdExpiresAt = null,
                baseFare = trip.price * 2, serviceCharge = Money.taka(60), discount = Money.ZERO, total = trip.price * 2 + Money.taka(60),
                contactPhone = "01712345678", contactEmail = null,
                segments = listOf(
                    BookingSegment(
                        trip.operatorName, trip.coachNumber, trip.from.id, trip.to.id, trip.departAt, trip.arriveAt, "ac",
                        listOf("A1", "A3"), point, point.copy(nameEn = "Kolatoli", nameBn = "কলাতলী"),
                    ),
                ),
                passengers = listOf(
                    BookingPassenger("রহিম উদ্দিন", Gender.MALE, 34, "A1", "••••••7890"),
                    BookingPassenger("Karima Begum", Gender.FEMALE, null, "A3", null),
                ),
            ),
        )
    }

    @Test fun paymentSuccess() = shot("07_payment_success") {
        PaymentResultScreen(success = true, onRetry = {}, onViewTrips = {}, onGoHome = {})
    }

    @Test fun eTicket() = shot("07b_e_ticket") {
        val trip = MockContent.busTrips(today.plusDays(1), dhakaCox.from, dhakaCox.to).first()
        val point = StopPoint("p", "Kalabagan", "কলাবাগান", trip.departAt.minusMinutes(15))
        TicketResultScreen(
            booking = Booking(
                id = "b1", ref = "JTW6FX", mode = TravelMode.BUS, status = BookingStatus.TICKETED, holdExpiresAt = null,
                baseFare = trip.price * 2, serviceCharge = Money.taka(60), discount = Money.ZERO, total = trip.price * 2 + Money.taka(60),
                contactPhone = "01712345678", contactEmail = null,
                segments = listOf(
                    BookingSegment(trip.operatorName, trip.coachNumber, trip.from.id, trip.to.id, trip.departAt, trip.arriveAt, "ac", listOf("A2", "A3"), point, null),
                ),
                passengers = listOf(
                    BookingPassenger("রহিম উদ্দিন", Gender.MALE, 34, "A2", null, "PNR-1A2B3C4D-1"),
                    BookingPassenger("Karima Begum", Gender.FEMALE, null, "A3", null, "PNR-1A2B3C4D-2"),
                ),
                ticket = Ticket("JT1.JTW6FX.fc371ebba14b4d51af63ae46e00e19b4.Qm9vZ3Vz", "/api/v1/tickets/b1/pdf", java.time.Instant.EPOCH),
            ),
            from = dhakaCox.from,
            to = dhakaCox.to,
            pdfBusy = false,
            onOpenPdf = {}, onSharePdf = {}, onViewTrips = {}, onGoHome = {},
        )
    }

    // ---- Phase 5: flights ------------------------------------------------------------

    private val air get() = MockContent.popularRoutes.first { it.mode == TravelMode.FLIGHT }

    /** Round trips built from the sample one-way offers (outbound + return). */
    private fun roundTrips(): List<com.jatri.core.model.FlightOffer> {
        val out = MockContent.flightOffers(today.plusDays(10), air.from, air.to)
        val back = MockContent.flightOffers(today.plusDays(14), air.to, air.from).reversed()
        return out.mapIndexed { i, o ->
            val b = back[i]
            val base = o.baseTotal + b.baseTotal
            val tax = o.taxTotal + b.taxTotal
            o.copy(
                id = "rt$i", journeys = o.journeys + b.journeys, baseTotal = base, taxTotal = tax, price = base + tax + o.serviceCharge,
                fares = listOf(com.jatri.core.model.PaxFare(com.jatri.core.model.PassengerType.ADULT, 1, base, tax)),
            )
        }
    }

    @Test fun flightSearch() = shot("12_flight_search") {
        HomeScreen(
            state = HomeUiState(
                query = SearchQuery(
                    TravelMode.FLIGHT, air.from, air.to, today.plusDays(10), returnDate = today.plusDays(14),
                    passengers = PassengerCount(2, 1), tripType = com.jatri.core.model.TripType.ROUND_TRIP,
                ),
                today = today,
            ),
            actions = HomeActions(),
            now = LocalTime.of(9, 0),
        )
    }

    @Test fun flightMultiCity() = shot("12b_flight_multi_city") {
        val cgp = MockContent.popularRoutes.first { it.mode == TravelMode.FLIGHT && it.to.id == "air_CGP" }.to
        HomeScreen(
            state = HomeUiState(
                query = SearchQuery(
                    TravelMode.FLIGHT, air.from, air.to, today.plusDays(10), tripType = com.jatri.core.model.TripType.MULTI_CITY,
                    extraLegs = listOf(com.jatri.core.model.LegQuery(air.to, cgp, today.plusDays(12)), com.jatri.core.model.LegQuery(cgp, null, today.plusDays(14))),
                ),
                today = today,
            ),
            actions = HomeActions(),
            now = LocalTime.of(9, 0),
        )
    }

    @Test fun flightResults() = shot("13_flight_results") {
        com.jatri.feature.flight.FlightResultsScreen(
            state = com.jatri.feature.flight.FlightResultsUiState(
                args = com.jatri.feature.flight.navigation.FlightResultsDestination(
                    "${air.from.id},${air.to.id},${today.plusDays(10)};${air.to.id},${air.from.id},${today.plusDays(14)}", 2, 1, 0, "ECONOMY", "ROUND_TRIP",
                ),
                places = listOf(air.from to air.to, air.to to air.from),
                loading = false,
                offers = roundTrips(),
                filters = com.jatri.feature.flight.FlightFilters(refundableOnly = false, maxStops = 0),
            ),
        )
    }

    @Test fun flightDetails() = shot("14_flight_details") {
        val offer = roundTrips()[1]
        com.jatri.feature.flight.FlightDetailsScreen(
            state = com.jatri.feature.flight.FlightDetailsUiState(loading = false, offer = offer, priceChangedFrom = offer.price - Money.taka(250)),
        )
    }

    @Test fun flightPassengers() = shot("15_flight_passengers") {
        val offer = roundTrips()[0].copy(international = true)
        com.jatri.feature.booking.FlightPassengerScreen(
            state = com.jatri.feature.booking.FlightPassengerUiState(
                loading = false,
                offer = offer,
                phone = "01712345678",
                passengers = listOf(
                    com.jatri.feature.booking.FlightPassengerForm(
                        com.jatri.core.model.PassengerType.ADULT, "RAHIM", "UDDIN", Gender.MALE, "15081990", "A01234567", "01022027",
                        expiryError = com.jatri.feature.booking.FlightFieldError.PASSPORT_EXPIRY,
                    ),
                    com.jatri.feature.booking.FlightPassengerForm(
                        com.jatri.core.model.PassengerType.CHILD, "নুসরাত", "", null, "0107",
                        givenNameError = com.jatri.feature.booking.FlightFieldError.LATIN_ONLY,
                        surnameError = com.jatri.feature.booking.FlightFieldError.REQUIRED,
                        genderError = true,
                        dobError = com.jatri.feature.booking.FlightFieldError.DATE_INVALID,
                    ),
                ),
            ),
        )
    }

    private fun flightBooking(status: BookingStatus): Booking {
        val offer = roundTrips()[1]
        val segs = offer.journeys.flatMapIndexed { j, journey ->
            journey.legs.map { l ->
                BookingSegment(l.carrierName, l.flightNumber, l.from.id, l.to.id, l.departAt, l.arriveAt, "economy", emptyList(), null, null,
                    journeyIndex = j, aircraft = l.aircraft, baggage = "20 kg")
            }
        }
        val ticketed = status == BookingStatus.TICKETED
        return Booking(
            id = "f1", ref = "JTKZ5H", mode = TravelMode.FLIGHT, status = status, holdExpiresAt = null,
            baseFare = offer.baseTotal, taxes = offer.taxTotal, serviceCharge = offer.serviceCharge, discount = Money.ZERO, total = offer.price,
            contactPhone = "01712345678", contactEmail = null, segments = segs,
            passengers = listOf(
                BookingPassenger("RAHIM UDDIN", Gender.MALE, 36, null, "•••••4567", if (ticketed) "000-7506468715" else null),
                BookingPassenger("NUSRAT JAHAN", Gender.FEMALE, 8, null, null, if (ticketed) "000-9586562860" else null, com.jatri.core.model.PassengerType.CHILD),
            ),
            ticket = if (ticketed) Ticket("JT1.JTKZ5H.fc371ebba14b4d51af63ae46e00e19b4.Qm9vZ3Vz", "/pdf", java.time.Instant.EPOCH) else null,
            cabinClass = offer.cabinClass, fareBrand = offer.fareBrand, fareRules = offer.rules,
        )
    }

    @Test fun flightReviewPay() = shot("16_flight_review_pay") {
        ReviewPayScreen(onBack = {}, onPaid = {}, secondsLeft = 8 * 60 + 40, onCancel = {}, booking = flightBooking(BookingStatus.HELD))
    }

    @Test fun flightTicket() = shot("17_flight_e_ticket") {
        TicketResultScreen(
            booking = flightBooking(BookingStatus.TICKETED), from = air.from, to = air.to, pdfBusy = false,
            onOpenPdf = {}, onSharePdf = {}, onViewTrips = {}, onGoHome = {},
        )
    }

    // ---- Phase 6: trains -------------------------------------------------------------

    private val rail get() = MockContent.popularRoutes.first { it.mode == TravelMode.TRAIN }

    private fun trainSeatMap(): com.jatri.core.model.TrainSeatMap {
        val trip = MockContent.trainTrips(today.plusDays(3), rail.from, rail.to).first()
        fun coach(name: String, booked: Set<Int>, held: Set<Int>) = com.jatri.core.model.TrainCoach(
            name, berth = false,
            layout = com.jatri.core.model.SeatLayout(
                (0 until 15).map { r ->
                    (1..4).flatMap { c ->
                        val n = r * 4 + c
                        val state = when (n) {
                            in booked -> com.jatri.core.model.SeatState.BOOKED
                            in held -> com.jatri.core.model.SeatState.HELD
                            else -> com.jatri.core.model.SeatState.AVAILABLE
                        }
                        val seat = com.jatri.core.model.Seat("$name-$n", state)
                        if (c == 3) listOf(null, seat) else listOf(seat)
                    }
                },
            ),
        )
        return com.jatri.core.model.TrainSeatMap(
            trip, com.jatri.core.model.TrainClass.SNIGDHA, Money.taka(655),
            listOf(coach("CHA", setOf(1, 2, 3, 5, 8, 9, 12, 13, 14, 20, 21, 26, 33, 40, 41, 47, 52, 55), setOf(17, 18)), coach("JA", (1..40).toSet(), emptySet())),
            4, Money.taka(20),
        )
    }

    @Test fun trainResults() = shot("18_train_results") {
        com.jatri.feature.train.TrainResultsScreen(
            state = com.jatri.feature.train.TrainResultsUiState(
                args = com.jatri.feature.train.navigation.TrainResultsDestination(rail.from.id, rail.to.id, today.plusDays(3).toString(), "SNIGDHA", 2, 0),
                from = rail.from, to = rail.to, loading = false,
                trips = MockContent.trainTrips(today.plusDays(3), rail.from, rail.to),
            ),
            today = today,
        )
    }

    @Test fun trainSeats() = shot("19_train_seats") {
        com.jatri.feature.train.TrainSeatScreen(
            state = com.jatri.feature.train.TrainSeatsUiState(loading = false, map = trainSeatMap(), selected = listOf("CHA-6", "CHA-7")),
        )
    }

    @Test fun trainPassengers() = shot("20_train_passengers") {
        com.jatri.feature.booking.TrainPassengerScreen(
            state = com.jatri.feature.booking.TrainPassengerUiState(
                loading = false, seatMap = trainSeatMap(), phone = "01712345678",
                passengers = listOf(
                    com.jatri.feature.booking.PassengerForm("CHA-6", false, "রহিম উদ্দিন", Gender.MALE, "34", "", nidError = true),
                    com.jatri.feature.booking.PassengerForm("CHA-7", false, "Ayesha Rahman", Gender.FEMALE, "9"),
                ),
            ),
        )
    }

    private fun trainBooking(status: BookingStatus): Booking {
        val trip = MockContent.trainTrips(today.plusDays(3), rail.from, rail.to).first()
        val ticketed = status == BookingStatus.TICKETED
        return Booking(
            id = "t1", ref = "JT8EU3", mode = TravelMode.TRAIN, status = status, holdExpiresAt = null,
            baseFare = Money.taka(1310), serviceCharge = Money.taka(40), discount = Money.ZERO, total = Money.taka(1350),
            contactPhone = "01712345678", contactEmail = null,
            segments = listOf(
                BookingSegment(trip.trainName, trip.trainNumber, trip.from.id, trip.to.id, trip.departAt, trip.arriveAt, "snigdha", listOf("CHA-6", "CHA-7"), null, null, coach = "CHA"),
            ),
            passengers = listOf(
                BookingPassenger("রহিম উদ্দিন", Gender.MALE, 34, "CHA-6", "•••••••••6789", if (ticketed) "PNR-783E0218-1" else null),
                BookingPassenger("Ayesha Rahman", Gender.FEMALE, 9, "CHA-7", null, if (ticketed) "PNR-783E0218-2" else null),
            ),
            ticket = if (ticketed) Ticket("JT1.JT8EU3.fc371ebba14b4d51af63ae46e00e19b4.Qm9vZ3Vz", "/pdf", java.time.Instant.EPOCH) else null,
        )
    }

    @Test fun trainReviewPay() = shot("21_train_review_pay") {
        ReviewPayScreen(onBack = {}, onPaid = {}, secondsLeft = 9 * 60 + 5, onCancel = {}, booking = trainBooking(BookingStatus.HELD))
    }

    @Test fun trainTicket() = shot("22_train_e_ticket") {
        TicketResultScreen(
            booking = trainBooking(BookingStatus.TICKETED), from = rail.from, to = rail.to, pdfBusy = false,
            onOpenPdf = {}, onSharePdf = {}, onViewTrips = {}, onGoHome = {},
        )
    }

    @Test fun trips() = shot("08_trips") { TripsScreen(onSearchTickets = {}, onTicketClick = {}) }

    @Test fun offers() = shot("09_offers") { OffersScreen(MockContent.offers(today)) }

    @Test fun account() = shot("10_account") {
        AccountScreen(UserSettings(), {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun states() = shot("11_states") {
        Column(Modifier.fillMaxSize()) {
            EmptyState(
                title = "Empty",
                message = "This route has no tickets right now. Try another date.",
                modifier = Modifier.weight(1f),
            )
            ErrorState(onRetry = {}, modifier = Modifier.weight(1f))
        }
    }
}

@Config(qualifiers = "bn-w400dp-h880dp-xhdpi")
class BanglaLightScreenshots : ScreenshotTest("bn_light", dark = false)

@Config(qualifiers = "en-w400dp-h880dp-xhdpi")
class EnglishLightScreenshots : ScreenshotTest("en_light", dark = false)

@Config(qualifiers = "bn-w400dp-h880dp-night-xhdpi")
class BanglaDarkScreenshots : ScreenshotTest("bn_dark", dark = true)
