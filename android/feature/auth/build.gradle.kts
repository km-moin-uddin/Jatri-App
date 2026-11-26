plugins {
    alias(libs.plugins.jatri.android.feature)
}

dependencies {
    // SMS Retriever API: reads the OTP from the SMS without any SMS permission.
    implementation(libs.play.services.auth.api.phone)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
