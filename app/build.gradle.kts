plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.confighub.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.confighub.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-skeleton"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // ---- Real tunnel library (step 2, Android Studio linking required) ----
    // Official open-source sing-box Android library to link:
    //   SagerNet/sing-box — libbox (https://github.com/SagerNet/sing-box)
    // Its Android bindings are built from that repo with gomobile; there
    // is NO official Maven Central AAR to blindly paste a version for,
    // so do NOT invent one here. In Android Studio, in order:
    //   1) Build libbox.aar from the SagerNet/sing-box repo (or use the
    //      NekoBox libcore fork of the same open-source code),
    //   2) drop the built AAR in app/libs/,
    //   3) uncomment the line below and sync Gradle.
    // Until steps 1-3 are done, ConfigHubVpnService builds a TUN but
    // the native sing-box call is a TODO, and the app does NOT connect
    // a real VPN. Do not add a proprietary binary.
    // implementation(files("libs/libbox.aar"))
}
