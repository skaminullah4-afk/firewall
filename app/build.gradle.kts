plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.firewall.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.firewall.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

dependencies {
    implementation("com.batoulapps.adhan:adhan2:0.0.7")
}

    kotlinOptions {
        jvmTarget = "17"
    }
}
