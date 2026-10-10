import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    // Unit tests run JVM code that logs through android.util.Log; stubs return defaults instead of throwing.
    testOptions { unitTests.isReturnDefaultValues = true }
    namespace = "com.undersigned.app"
    compileSdk = 35

    defaultConfig {
        // Ship only the locales this app actually translates (fleet rule: the store reads the APK's locale set).
        resourceConfigurations += listOf("en", "ar", "bn", "cs", "de", "el", "es", "fil", "fr", "hi", "hu", "in", "it", "ja", "ko", "nl", "pl", "pt-rBR", "ro", "ru", "sv", "th", "tr", "uk", "ur", "vi", "zh-rCN", "zh-rTW")
        applicationId = "com.undersigned.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.1.1"
        // Keyed RPC provider from local.properties — never in source control. Empty = public pools only.
        resValue("string", "helius_api_key", localProperties.getProperty("HELIUS_API_KEY", ""))
        // Fleet last-resort key: lives ONLY in ~/.gradle/gradle.properties (never committed). Appended LAST.
        resValue("string", "helius_api_key_3", (project.findProperty("HELIUS_API_KEY_3") as? String) ?: localProperties.getProperty("HELIUS_API_KEY_3", ""))
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release-keystore.jks")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            // Sign v2 AND v3, matching the rest of the fleet. The build was emitting v2 only;
            // v3 carries the signing-certificate lineage Android uses to reason about an app's
            // identity. Same key, so installs still update in place. v1 adds nothing above minSdk 24.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "win32-x86/**"; excludes += "win32-x86-64/**" } }
}

dependencies {
    implementation(project(":protocol"))
    implementation("androidx.core:core-ktx:1.12.0")
    // Android 12+ splash API: the mark on black for the cold-start frame, then the app theme (design §6).
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Solana Mobile Wallet Adapter (SIWS + signAndSendTransactions)
    // MWA clientlib-ktx 2.0.3 declares androidx.test.ext:junit (and so androidx.test:core and junit) as runtime
    // dependencies: test code shipped in the release APK and added REORDER_TASKS to its permissions (found 7 Oct 2026).
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.3") {
        exclude(group = "androidx.test.ext"); exclude(group = "androidx.test"); exclude(group = "junit"); exclude(group = "org.hamcrest")
    }
    // Periodic ledger watch for local notifications (followed apps, replies to my reviews)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // dApp icons from the store's icon URIs (Toolkit pattern)
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Ed25519 for the phone's quick-review app key (lightweight API only; R8 keeps what is used).
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // Seeker Genesis Token check (fleet library, read-only)
    implementation("com.github.aardappvark:seeker-verify:1.1.0")
    testImplementation("org.json:json:20240303")   // the real org.json: Android's is a stub in JVM tests
    testImplementation("junit:junit:4.13.2")
}
