plugins {
    id("anikuta.library")
    id("app.cash.sqldelight")
}

android {
    namespace = "app.confused.anikuta.core.database"
}

sqldelight {
    databases {
        create("AnikutaDatabase") {
            packageName.set("app.confused.anikuta.core.database")
            // Dialect defaults to the latest SQLite; no explicit dialect needed
        }
    }
}

dependencies {
    implementation(libs.bundles.sqldelight)
    implementation(libs.sqldelight.dialects.sql)
    implementation(kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
