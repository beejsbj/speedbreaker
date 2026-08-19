plugins {
    id("com.android.application")
}

android {
    namespace = "dev.burooj.speedbreaker.probe"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.burooj.speedbreaker.probe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-probe"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

