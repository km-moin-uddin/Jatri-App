plugins {
    alias(libs.plugins.jatri.android.library)
    alias(libs.plugins.jatri.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    api(projects.core.network)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
}
