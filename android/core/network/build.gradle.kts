plugins {
    alias(libs.plugins.jatri.android.library)
    alias(libs.plugins.jatri.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        // 10.0.2.2 is the host machine from the Android emulator. Overridden per build type later.
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/api/v1/\"")
    }
}

dependencies {
    implementation(projects.core.common)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp.core)
    implementation(libs.okhttp.logging)
}
