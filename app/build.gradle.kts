plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kz.musiccapsule.app"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            storeFile = file("music-capsule-debug.jks")
            storePassword = "musiccapsule"
            keyAlias = "music-capsule"
            keyPassword = "musiccapsule"
        }
    }

    defaultConfig {
        applicationId = "kz.musiccapsule.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "7.0.0"

        signingConfig = signingConfigs.getByName("debug")
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
