plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // Java sources: Palantir formatter (4-space indentation, conventional wrapping) - the
    // closest mainstream auto-formatter to IntelliJ IDEA's Java defaults.
    java {
        target("app/src/**/*.java", "libraries/sdk/src/**/*.java")
        targetExclude("**/build/**")
        palantirJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    // Kotlin sources: ktlint configured (via .editorconfig) to the intellij_idea code style.
    kotlin {
        target("app/src/**/*.kt", "libraries/sdk/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target(
            "build.gradle.kts",
            "settings.gradle.kts",
            "app/build.gradle.kts",
            "libraries/sdk/build.gradle.kts",
        )
        ktlint()
    }
}
