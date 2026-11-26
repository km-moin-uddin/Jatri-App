plugins {
    alias(libs.plugins.jatri.android.library)
    alias(libs.plugins.jatri.android.compose)
}

dependencies {
    api(projects.core.common)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.tooling.preview)
}
