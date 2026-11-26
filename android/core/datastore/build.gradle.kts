plugins {
    alias(libs.plugins.jatri.android.library)
    alias(libs.plugins.jatri.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
}
