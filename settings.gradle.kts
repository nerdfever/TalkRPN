// Top-level Gradle settings: where to find plugins, where to find libraries,
// and which sub-projects (modules) make up this build.

pluginManagement {

    // Repositories searched for the *build plugins* themselves (Android Gradle Plugin, Kotlin, etc).
    repositories {

        // Google's repo holds the Android tooling; the content filter keeps Gradle from
        // pointlessly querying it for non-Android artifacts.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }

        mavenCentral()

        gradlePluginPortal()
    }
}

dependencyResolutionManagement {

    // Refuse any repository declared inside a module's build file — all dependency
    // sources live here, in one place.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    // Repositories searched for the *libraries* the app code uses.
    repositories {
        google()
        mavenCentral()
    }
}

// The name of the whole build (shows up in Gradle output and IDE window titles).
rootProject.name = "TalkRPN"

// The modules: the watch app itself, and a tiny companion utility - a tile that
// toggles the watch's wireless-debugging setting, so development doesn't start
// with a scroll through Settings. Separate APK on purpose: dev tooling, not
// part of the calculator.
include(":app")
include(":wdbtile")

// ---------------------------------------------------------------------------
// Keep generated output off Google Drive.
//
// The source tree lives in My Drive so that it is backed up continuously, but a
// single build rewrites hundreds of megabytes of intermediates, which Drive would
// then dutifully upload. So: if local.properties names a build root on a local
// disk, every module's build/ directory is redirected there instead.
//
// This is opt-in. With no such entry the build behaves exactly like a stock
// Android project, writing build/ alongside the sources.
// ---------------------------------------------------------------------------

// Read the machine-specific settings file, if this checkout has one.
val localProperties = java.util.Properties()
val localPropertiesFile = File(rootDir, "local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val buildRoot = localProperties.getProperty("buildDir.root")

// Redirect each project as Gradle gets to it, which is the only point at which
// a project's layout exists but nothing has been written to it yet.
if (!buildRoot.isNullOrBlank()) {
    gradle.beforeProject {

        // Gradle names projects ":" and ":app"; turn that into a directory name
        // so the root project and its modules do not land on top of each other.
        val relativePath = project.path.removePrefix(":").replace(':', '/').ifEmpty { "root" }

        project.layout.buildDirectory.set(File(buildRoot, relativePath))
    }
}
