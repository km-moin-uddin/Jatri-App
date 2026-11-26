plugins {
    alias(libs.plugins.jatri.android.library)
    alias(libs.plugins.jatri.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Where the app talks to. Override with -Pjatri.apiBaseUrl=https://api.example.com/api/v1/
// and -Pjatri.useMockApi=false to use a real backend. The default mock lets the APK run on a
// phone with no server (OTP 123456).
val apiBaseUrl = providers.gradleProperty("jatri.apiBaseUrl").getOrElse("http://10.0.2.2:8000/api/v1/")
val useMockApi = providers.gradleProperty("jatri.useMockApi").getOrElse("true").toBoolean()

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("boolean", "USE_MOCK_API", useMockApi.toString())
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
