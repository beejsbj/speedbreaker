plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.burooj.speedbreaker.fixture"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.burooj.speedbreaker.fixture"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
