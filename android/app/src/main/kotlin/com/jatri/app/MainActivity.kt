package com.jatri.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jatri.app.ui.JatriApp
import com.jatri.core.common.network.NetworkMonitor
import com.jatri.core.data.repository.AuthRepository
import com.jatri.core.data.repository.PaymentReturnEvents
import com.jatri.core.model.SessionState
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.ThemeMode
import com.jatri.feature.auth.navigation.OnboardingDestination
import com.jatri.feature.home.navigation.HomeDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single activity. AppCompatActivity (not ComponentActivity) so per-app language works on
 * Android 7–12 too, via AppCompatDelegate.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var networkMonitor: NetworkMonitor

    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var paymentReturns: PaymentReturnEvents

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        handlePaymentReturn(intent)
        // Keep the splash until settings are read (DataStore is fast, well under the ~1 s budget).
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }

        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is MainUiState.Ready) applyLanguage(state.settings.language)
                }
            }
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val settings = (state as? MainUiState.Ready)?.settings ?: return@setContent
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Decide the first screen once; later settings changes must not reset navigation.
            val start: Any = remember { if (settings.onboardingCompleted) HomeDestination else OnboardingDestination }
            // Screens sit on a light/dark canvas, so system bar icons follow the app theme.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            JatriTheme(darkTheme = dark) {
                JatriApp(
                    startDestination = start,
                    networkMonitor = networkMonitor,
                    isLoggedIn = { authRepository.session.value is SessionState.LoggedIn },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePaymentReturn(intent)
    }

    /** jatri://payment/return?payment_id=… : the payment screen re-checks the status at once. */
    private fun handlePaymentReturn(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "jatri" && data.host == "payment") {
            data.getQueryParameter("payment_id")?.takeIf { it.isNotBlank() }?.let(paymentReturns::onReturn)
        }
    }

    /** DataStore is the source of truth; AppCompat applies it and recreates the activity if needed. */
    private fun applyLanguage(language: AppLanguage) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (current != language.tag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
