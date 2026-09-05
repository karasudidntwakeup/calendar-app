plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.karasu.calendarapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.karasu.calendarapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "calendar123"
            keyAlias = "calendar"
            keyPassword = "calendar123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.foundation:foundation:1.8.0")
    // Material 3 Expressive (Android 16+ style): MaterialExpressiveTheme,
    // MotionScheme.expressive(), MaterialShapes, wavy progress indicators.
    // Public API lives on the alpha line (internalized in 1.4.0 stable).
    implementation("androidx.compose.material3:material3:1.4.0-alpha10") {
        because("Material 3 Expressive APIs")
    }
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
}
