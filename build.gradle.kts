// Root build file.
//
// It declares which plugins the build *may* use, but applies none of them here —
// that is what "apply false" means. The :app module then applies the ones it needs.
// Declaring them once at the root is how Gradle keeps every module on the same
// plugin version.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
