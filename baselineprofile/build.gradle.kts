plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

// Generates app/src/release/generated/baselineProfiles (./gradlew :app:generateBaselineProfile)
// and measures startup / scrolling on a release-like build (connectedBenchmarkReleaseAndroidTest).
android {
    namespace = "com.goomistudio.postmory.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile {
    // The emulator / phone already connected (no Gradle-managed device).
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
