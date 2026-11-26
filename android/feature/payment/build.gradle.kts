plugins {
    alias(libs.plugins.jatri.android.feature)
}

dependencies {
    // Chrome Custom Tabs: the gateway page opens in a browser tab, never inside a WebView.
    implementation(libs.androidx.browser)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
