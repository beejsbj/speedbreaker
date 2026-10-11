plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.burooj.speedbreaker.fixture"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.burooj.speedbreaker.fixture"
        minSdk = 26
        // Test utility needs to set the disposable AVD's global DND visual policy.
        // Android 15 scopes this API to an implicit rule for apps targeting 35+.
        targetSdk = 34
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
