// Build file for the :repl module - a plain-JVM process wrapping the engine
// and the formatter behind a line protocol, so the Python button-pad tester
// in tools/ exercises THE engine rather than a reimplementation.

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("com.nerdfever.talkrpn.repl.MainKt")
}

// No toolchain pin: this process runs on the same JDK that runs the build
// (a dev-machine tool, not a shipped artifact), so demanding a specific
// version would only force a second JDK install for nothing.

sourceSets {
    main {
        kotlin {
            // The engine, the formatter, the token vocabulary and the
            // readout rules are compiled STRAIGHT FROM the app module's
            // sources - one copy in the repository, so this process can
            // never drift from what the watch runs. All four files are
            // pure Kotlin with no Android in them, which is what makes
            // this legal. The include filter applies to every source root,
            // so this module's own files must be named here too.
            srcDir("../app/src/main/java")
            include(
                "**/RpnEngine.kt",
                "**/NumberFormatter.kt",
                "**/TokenWords.kt",
                "**/RegisterReadout.kt",
                "**/repl/**",
            )
        }
    }
}
