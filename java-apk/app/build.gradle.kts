plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "come.tshah.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "come.tshah.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
        buildConfigField("String", "CHANNEL_ID", "\"${project.findProperty("CHANNEL_ID") ?: "ca"}\"")
        buildConfigField("String", "BACKEND_URL", "\"${project.findProperty("BACKEND_URL") ?: ""}\"")
        buildConfigField("String", "APP_URL", "\"${project.findProperty("APP_URL") ?: "https://www.google.com"}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    signingConfigs {
        create("debugkey") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debugkey")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugkey")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.activity:activity:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.6.2")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("com.google.guava:guava:32.0.1-android")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
}
