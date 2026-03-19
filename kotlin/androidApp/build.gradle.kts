import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
    kotlin("plugin.compose")
    id("dev.gobley.cargo") version "0.3.7"
    id("dev.gobley.uniffi") version "0.3.7"
    kotlin("plugin.atomicfu") version "2.1.0"
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

android {
    namespace = "tech.bananajuice.adzuki.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "tech.bananajuice.adzuki.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
    implementation(project(":kotlin:shared"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    testImplementation("junit:junit:4.13.2")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
}
