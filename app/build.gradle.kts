// Build file for the watch app module.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {

    // The Kotlin/Java package that generated code (BuildConfig, R) lands in.
    namespace = "com.nerdfever.talkrpn"

    // The Android API level we compile against — the newest the build tools support.
    // Compiling against a newer platform than the watch runs is normal and expected:
    // compile new, run old.
    compileSdk = 37

    defaultConfig {

        // The identity of the installed app on the device. Changing it later would look
        // like a different app entirely, so it is chosen once and left alone.
        applicationId = "com.nerdfever.talkrpn"

        // Oldest Android version we support. API 34 is Wear OS 5 — what the Galaxy
        // Watch7 shipped with, and the target hardware for this project.
        //
        // Chosen for the speech APIs rather than arbitrarily. The pieces this app
        // needs arrived across three releases:
        //
        //   API 31  createOnDeviceSpeechRecognizer, isOnDeviceRecognitionAvailable
        //   API 33  checkRecognitionSupport, RecognitionSupport, EXTRA_BIASING_STRINGS
        //   API 34  triggerModelDownload with a progress listener
        //
        // Setting minSdk to 34 means none of that needs a version guard. The cost is
        // excluding Wear OS 3 and 4 devices, which is no cost at all here.
        minSdk = 34

        // The API level whose behaviour we opt in to. From 31 August 2026 the Play Store
        // requires at least 35 for Wear OS apps (36 for phones); 36 clears both bars while
        // staying on a platform that shipping watches actually run.
        targetSdk = 36

        // Internal build counter, and the human-readable version shown to a user.
        versionCode = 1
        versionName = "1.0"

        // NOTE for any future native dependency: this watch is 32-bit only.
        // Measured, not assumed — ro.product.cpu.abi = armeabi-v7a and
        // ro.product.cpu.abilist64 is empty, despite the Exynos W1000 having 64-bit
        // cores. Filtering to arm64-v8a produces INSTALL_FAILED_NO_MATCHING_ABIS.
        // No abiFilters block is needed while the app ships no native code of its own.
    }

    buildTypes {
        release {

            // No code shrinking for now — it adds a whole class of confusing failures
            // and buys us nothing on an app this size.
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {

        // Bytecode level for any Java sources. AGP keeps the Kotlin compiler in step
        // with this automatically, so it only needs stating once.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {

        // Turn on the Compose compiler for this module.
        compose = true
    }
}

dependencies {

    // Kotlin extensions over the core Android framework.
    implementation(libs.androidx.core.ktx)

    // The BOM must come first: it fixes the versions that the un-versioned
    // Compose artifacts below will resolve to.
    implementation(platform(libs.androidx.compose.bom))

    // Core Compose UI toolkit, plus the glue that lets an Activity host it.
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)

    // Wear-specific Compose: layout primitives and watch-styled widgets.
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)

    // Tooling used only by @Preview in the IDE.
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.tooling.preview)

    // The preview *renderer* is a debug-only dependency, so it never ships in a release APK.
    debugImplementation(libs.androidx.compose.ui.tooling)
}
