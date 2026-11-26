package com.jatri.core.network.di

import com.jatri.core.datastore.session.SessionStore
import com.jatri.core.network.BuildConfig
import com.jatri.core.network.api.JatriApi
import com.jatri.core.network.auth.AuthHeaderInterceptor
import com.jatri.core.network.auth.SessionEvents
import com.jatri.core.network.auth.TokenAuthenticator
import com.jatri.core.network.mock.MockJatriApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Networking stack: OkHttp (timeouts for slow networks, auth header, token refresh on 401),
 * Retrofit + Kotlinx Serialization, and the switch between the real API and the mock backend.
 * Certificate pinning is added in Phase 9 once the production domain is known.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        // Generous timeouts: many users are on slow 3G/4G.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC never logs headers or bodies, so tokens and NID numbers stay out of logs.
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }

    @Provides
    @Singleton
    @Named("refresh")
    fun providesRefreshClient(): OkHttpClient = baseClient().build()

    @Provides
    @Singleton
    fun providesOkHttpClient(
        store: SessionStore,
        events: SessionEvents,
        authHeader: AuthHeaderInterceptor,
        @Named("refresh") refreshClient: OkHttpClient,
        json: Json,
    ): OkHttpClient = baseClient()
        .addInterceptor(authHeader)
        .authenticator(TokenAuthenticator(store, events, BuildConfig.API_BASE_URL, refreshClient, json))
        .build()

    @Provides
    @Singleton
    fun providesRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun providesJatriApi(retrofit: dagger.Lazy<Retrofit>, mock: dagger.Lazy<MockJatriApi>): JatriApi =
        if (BuildConfig.USE_MOCK_API) mock.get() else retrofit.get().create(JatriApi::class.java)

    @Provides
    fun providesUsesMockApi(): MockFlag = MockFlag(BuildConfig.USE_MOCK_API)
}

/** Lets other modules know whether the mock backend is active (e.g. to show "OTP 123456"). */
data class MockFlag(val enabled: Boolean)
