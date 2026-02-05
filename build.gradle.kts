// Project レベルの build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false // これが重要
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}