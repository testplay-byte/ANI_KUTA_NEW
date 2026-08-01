plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "app.confused.anikuta.feature.setupwizard"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.preferences)
    implementation(projects.core.ads)

    // Koin
    implementation(libs.koin.androidx.compose)

    // Lifecycle
    implementation(androidx.lifecycle.runtimektx)
    implementation(androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Activity result contracts
    implementation("androidx.activity:activity-compose:1.9.3")

    // Material icons extended (for Icons.Filled.* used in the wizard)
    implementation("androidx.compose.material:material-icons-extended:1.7.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
