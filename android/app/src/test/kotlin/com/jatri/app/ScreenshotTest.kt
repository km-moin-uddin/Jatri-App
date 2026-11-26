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
