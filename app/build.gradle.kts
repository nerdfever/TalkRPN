// Build file for the watch app module.

import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// The Vosk speech model.
//
// 67 MB unpacked, which is far too much to keep in the source tree - the project
// lives on Google Drive and every byte would sync. It is also a third-party
// artifact that can be re-downloaded at will, so it is treated exactly like build
// output: fetched on demand into the redirected build directory on SSD.
//
// The consequence worth stating: a clean checkout needs one network round trip
// before its first build. After that the task is a no-op.
// ---------------------------------------------------------------------------

val voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

/**
 * Where the unpacked model lands. Under build/, so it follows the SSD redirect.
 *
 * Resolved to a plain File at configuration time rather than left as a Provider:
 * AGP 9 refuses Providers in the source set API, because it cannot tell generated
 * directories from static ones. The task ordering that a Provider would have carried
 * automatically is declared explicitly below instead.
 */
val voskAssetsDir: File = layout.buildDirectory.dir("vosk-assets").get().asFile

val fetchVoskModel = tasks.register("fetchVoskModel") {

    description = "Downloads and unpacks the Vosk speech model into the build directory."
    outputs.dir(voskAssetsDir)

    // Skip entirely when the model is already unpacked - this is the common case,
    // and re-checking a 67 MB tree on every build would be pointless work. The uuid
    // marker is part of the check because the model is useless to Vosk without it.
    outputs.upToDateWhen {
        File(voskAssetsDir, "model-en-us/am").exists() && File(voskAssetsDir, "model-en-us/uuid").exists()
    }

    doLast {

        val target = File(voskAssetsDir, "model-en-us")

        // Vosk's StorageService copies the model out of the APK into internal storage
        // on first run, and uses a `uuid` file to decide whether the unpacked copy is
        // stale. The models published on alphacephei.com do not contain one - Vosk's
        // own demo generates it during the build - so without this the load fails with
        // FileNotFoundException: model-en-us/uuid. Any stable string will do; using the
        // model name means swapping models later forces a re-extract, which is correct.
        fun writeUuid() = File(target, "uuid").writeText("vosk-model-small-en-us-0.15\n")

        if (target.resolve("am").exists()) {
            writeUuid()
            return@doLast
        }

        logger.lifecycle("Fetching Vosk model (~39 MB) — one time only...")
        target.mkdirs()

        // Stream the zip straight into place rather than staging a copy on disk.
        URI(voskModelUrl).toURL().openStream().use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->

                while (true) {
                    val entry = zip.nextEntry ?: break

                    // The archive wraps everything in a vosk-model-small-en-us-0.15/
                    // directory; strip it so the model files sit at the asset root,
                    // which is the layout Vosk's StorageService expects.
                    val relative = entry.name.substringAfter('/', "")
                    if (relative.isEmpty()) continue

                    val out = target.resolve(relative)

                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile.mkdirs()
                        out.outputStream().buffered().use { zip.copyTo(it) }
                    }
                }
            }
        }

        writeUuid()

        logger.lifecycle("Vosk model unpacked to $target")
    }
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

        // Vosk ships a ~10 MB native library per architecture, and by default all five
        // go into the APK - roughly 30 MB of code that can never run on anything we own.
        //
        // MEASURED, not assumed: the Galaxy Watch7 reports
        //     ro.product.cpu.abi       = armeabi-v7a
        //     ro.product.cpu.abilist64 = (empty)
        // so despite the Exynos W1000 having 64-bit cores, Wear OS runs a 32-bit
        // userspace on it and **armeabi-v7a is the only ABI it can install**. Filtering
        // to arm64-v8a produced INSTALL_FAILED_NO_MATCHING_ABIS.
        //
        // arm64-v8a is kept anyway for whatever 64-bit Wear device comes next, and
        // x86_64 for the emulator. A Play release would use an app bundle and let Play
        // split these; for sideloading an explicit filter does the same job.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
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

    sourceSets {
        named("main") {

            // The fetched model is an additional asset root alongside the normal one,
            // so nothing has to be copied into the source tree.
            assets.srcDir(voskAssetsDir)
        }
    }

    androidResources {

        // Vosk memory-maps parts of the model straight out of the APK. Compressed
        // assets cannot be mapped, so these must be stored uncompressed - otherwise
        // the model fails to load at runtime rather than at build time.
        noCompress += listOf("mdl", "fst", "conf", "int", "dubm", "ie", "carpa", "txt")
    }
}

// Make sure the model exists before assets are merged into the APK.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(fetchVoskModel)
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

    // Offline speech recognition: native Kaldi plus a thin Java API. No network code.
    implementation(libs.vosk.android)

    // Tooling used only by @Preview in the IDE.
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.tooling.preview)

    // The preview *renderer* is a debug-only dependency, so it never ships in a release APK.
    debugImplementation(libs.androidx.compose.ui.tooling)
}
