import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation("org.jetbrains.compose.foundation:foundation:1.10.2")
                implementation("org.jetbrains.compose.material3:material3:1.9.0-beta03")
                implementation("org.jetbrains.compose.ui:ui:1.10.2")
                implementation("org.jetbrains.compose.components:components-resources:1.10.2")
                implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.10.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting
    }
}

android {
    namespace = "tech.bananajuice.adzuki.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
