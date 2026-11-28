plugins {
    alias(libs.plugins.jatri.android.application)
    alias(libs.plugins.jatri.android.compose)
    alias(libs.plugins.jatri.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.jatri.app"

    defaultConfig {
        applicationId = "com.jatri.app"
        // CI passes -Pjatri.versionCode=<run number>; Play needs it to grow with every upload.
        versionCode = providers.gradleProperty("jatri.versionCode").getOrElse("9").toInt()
        versionName = providers.gradleProperty("jatri.versionName").getOrElse("0.9.0")
    }

    // The upload key never lives in the repo: CI writes it from a GitHub secret and passes its
    // path and passwords as environment variables. Without them release builds stay unsigned.
    val keystore = providers.environmentVariable("JATRI_KEYSTORE_FILE").orNull
    signingConfigs {
        if (keystore != null) {
            create("upload") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("JATRI_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("JATRI_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("JATRI_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        // Only ship the two languages we translate. Keeps the APK small.
        localeFilters += listOf("en", "bn")
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// A Play Store bundle must talk to the real backend over HTTPS. `bundleRelease` fails otherwise.
// (A minified release APK with the demo backend is still allowed for smoke tests.)
val checkStoreRelease by tasks.registering {
    val useMock = providers.gradleProperty("jatri.useMockApi").getOrElse("true").toBoolean()
    val url = providers.gradleProperty("jatri.apiBaseUrl").getOrElse("")
    val signed = providers.environmentVariable("JATRI_KEYSTORE_FILE").isPresent
    doLast {
        require(!useMock) { "Store builds need -Pjatri.useMockApi=false" }
        require(url.startsWith("https://")) { "Store builds need -Pjatri.apiBaseUrl=https://…" }
        require(signed) { "Store builds need the upload key: set JATRI_KEYSTORE_FILE and the passwords" }
    }
}
tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(checkStoreRelease) }

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.model)

    implementation(projects.feature.auth)
    implementation(projects.feature.home)
    implementation(projects.feature.flight)
    implementation(projects.feature.bus)
    implementation(projects.feature.train)
    implementation(projects.feature.booking)
    implementation(projects.feature.payment)
    implementation(projects.feature.trips)
    implementation(projects.feature.offers)
    implementation(projects.feature.account)
    implementation(projects.feature.notifications)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
