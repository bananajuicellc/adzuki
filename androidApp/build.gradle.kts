plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.blackbean.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.blackbean.android"
        minSdk = 24
        targetSdk = 34
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

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":shared"))
    testImplementation("junit:junit:4.13.2")
}
