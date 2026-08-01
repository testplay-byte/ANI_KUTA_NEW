// :core:ads
// Advertising preferences + (future) ad-rendering contracts.
//
// Phase 1 (current): AdsPreferences — persisted user choices for the in-app ad
// system (quota, cooldown, min-stay, ad URL). Consumed by the Setup Wizard's
// "Choose Your Poison" screen + by the (future) ad-rendering overlay.
//
// The ad *display* layer (WebView overlay, frequency capper, etc.) is a planned
// follow-up — this module is currently preference-only so it can be depended on
// by :feature:setup-wizard without pulling in rendering concerns.
plugins {
    id("anikuta.library")
}

android {
    namespace = "app.confused.anikuta.core.ads"
}

dependencies {
    implementation(project(":core:preferences"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
