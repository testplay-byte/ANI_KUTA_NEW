// ANIKUTA root build script
// Plugins are declared here with apply false; modules apply them as needed.

plugins {
    alias(androidx.plugins.application) apply false
    alias(androidx.plugins.library) apply false
    alias(kotlinx.plugins.android) apply false
    alias(kotlinx.plugins.compose.compiler) apply false
    alias(kotlinx.plugins.serialization) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
