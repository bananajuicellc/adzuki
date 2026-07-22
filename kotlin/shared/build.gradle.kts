import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")
    kotlin("plugin.compose")
    id("dev.gobley.cargo") version "0.3.7"
    id("dev.gobley.uniffi") version "0.3.7"
    kotlin("plugin.atomicfu") version "2.1.0"
}

android {
    namespace = "tech.bananajuice.adzuki.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(project(":kotlin:profile-picker"))
    implementation(libs.automerge)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

cargo {
    packageDirectory = layout.projectDirectory.dir("../../rust/adzuki")
}

uniffi {
    generateFromLibrary {
        namespace = "adzuki"
        packageName = "uniffi.adzuki"
    }
}
