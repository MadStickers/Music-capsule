plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kz.musiccapsule.app"
    compileSdk = 35

    val releaseStoreFile = providers.gradleProperty("MUSIC_CAPSULE_STORE_FILE")
    val releaseStorePassword = providers.gradleProperty("MUSIC_CAPSULE_STORE_PASSWORD")
    val releaseKeyAlias = providers.gradleProperty("MUSIC_CAPSULE_KEY_ALIAS")
    val releaseKeyPassword = providers.gradleProperty("MUSIC_CAPSULE_KEY_PASSWORD")

    signingConfigs {
        create("secureRelease") {
            if (releaseStoreFile.isPresent) {
                storeFile = rootProject.file(releaseStoreFile.get())
                storePassword = releaseStorePassword.orNull
                keyAlias = releaseKeyAlias.orNull
                keyPassword = releaseKeyPassword.orNull
            }
        }
    }

    defaultConfig {
        applicationId = "kz.musiccapsule.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 12
        versionName = "12.0.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("secureRelease")
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
