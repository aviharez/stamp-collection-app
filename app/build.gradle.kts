import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room3)
    alias(libs.plugins.baselineprofile)
}

// Firebase (Crashlytics + Analytics) needs app/google-services.json from the Firebase console. It is
// git-ignored (public repo); CI writes it from the GOOGLE_SERVICES_JSON secret. Without it the app still
// builds and runs, with Firebase simply not started (UsageStats does nothing).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

/**
 * Upload key for Play (keystore.properties in the project root, never committed):
 * storeFile=/absolute/path/upload.jks, storePassword=…, keyAlias=upload, keyPassword=…
 * Without it, release builds are unsigned (fine for checking R8; Play needs it signed).
 */
val uploadKey = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

/** Public support address (Profile → Send feedback, store listing, privacy policy). */
val SupportEmail = "hello.goomistudio@gmail.com"
/** Google's public test IDs (AdMob docs). */
val AdMobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val AdMobTestRewardedId = "ca-app-pub-3940256099942544/5224354917"

android {
    namespace = "com.aviharez.stamp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.aviharez.stamp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Profile → "Send feedback" / "Privacy policy". The support email is public (store listing), so it has a
        // default here; the privacy link shows once `privacyPolicyUrl` is set (gradle.properties).
        resValue("string", "support_email", providers.gradleProperty("supportEmail").filter { it.isNotBlank() }.getOrElse(SupportEmail))
        resValue("string", "privacy_policy_url", providers.gradleProperty("privacyPolicyUrl").getOrElse(""))
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug always uses Google's test ads (never tap your own real ads). Release reads the real
        // IDs from gradle properties (admobAppId / admobRewardedId, e.g. ~/.gradle/gradle.properties)
        // and falls back to the test IDs until the AdMob account exists.
        debug {
            manifestPlaceholders["admobAppId"] = AdMobTestAppId
            resValue("string", "admob_rewarded_unit", AdMobTestRewardedId)
        }
        release {
            manifestPlaceholders["admobAppId"] = providers.gradleProperty("admobAppId").filter { it.isNotBlank() }.getOrElse(AdMobTestAppId)
            resValue("string", "admob_rewarded_unit", providers.gradleProperty("admobRewardedId").filter { it.isNotBlank() }.getOrElse(AdMobTestRewardedId))
            // R8 shrink + optimise. The classic flags, not the newer `optimization {}` block: the
            // baseline profile plugin copies release into nonMinifiedRelease and only turns
            // isMinifyEnabled off, so with `optimization.enable` the profile came out obfuscated.
            isMinifyEnabled = true
            isShrinkResources = true
            if (uploadKey != null) signingConfig = signingConfigs.getByName("upload")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    // Per-app language (Android 13+ Settings → Apps → Postmory → Language) from the values-xx folders.
    androidResources {
        generateLocaleConfig = true
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Camera
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)

    // Storage
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.datastore.preferences)

    // Location (postmark)
    implementation(libs.play.services.location)

    // Billing (Pro: lifetime + yearly)
    implementation(libs.android.billing)

    // Ads (rewarded only, opt-in) + consent
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)

    // Startup / scroll speed: ship the generated baseline profile
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
