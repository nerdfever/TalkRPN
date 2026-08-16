// Build file for the wdbtile module - the wireless-debugging toggle tile.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {

    namespace = "com.nerdfever.wdbtile"

    // Same platform choices as the calculator, for the same reasons.
    compileSdk = 37

    defaultConfig {

        applicationId = "com.nerdfever.wdbtile"

        // Wear OS 5, the target hardware. Nothing here needs anything newer.
        minSdk = 34
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)

    // The tile itself: the service that supplies it, and the layout language
    // its UI is written in.
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)

    // TileService's contract returns ListenableFuture; guava supplies the
    // immediateFuture that satisfies it.
    implementation(libs.guava)

    // The fallback activity is Compose, like everything else of ours.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.material3)
}
