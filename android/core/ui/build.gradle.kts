plugins {
    alias(libs.plugins.jatri.android.library)
    alias(libs.plugins.jatri.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.zxing.core)
}
