plugins {
    alias(libs.plugins.jatri.android.library)
    alias(libs.plugins.jatri.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)
}
