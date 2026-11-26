plugins {
    alias(libs.plugins.jatri.android.feature)
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
