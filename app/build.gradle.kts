import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Load signing config from keystore.properties or environment variables
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(keystorePropsFile.inputStream())
}
fun keystoreProp(key: String, envKey: String): String? =
    (keystoreProps.getProperty(key) as String?)?.takeIf(String::isNotBlank)
        ?: System.getenv(envKey)?.takeIf(String::isNotBlank)

android {
    namespace = "com.example.ai_quota_monitor_android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.ai_quota_monitor_android"
        minSdk = 31
        targetSdk = 36
        versionCode = 10
        versionName = "1.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val sf = keystoreProp("storeFile", "AI_QUOTA_RELEASE_STORE_FILE")
                ?: error("Release signing: storeFile not set. Create keystore.properties or set AI_QUOTA_RELEASE_STORE_FILE.")
            storeFile = rootProject.file(sf)
            storePassword = keystoreProp("storePassword", "AI_QUOTA_RELEASE_STORE_PASSWORD")
                ?: error("Release signing: storePassword not set.")
            keyAlias = keystoreProp("keyAlias", "AI_QUOTA_RELEASE_KEY_ALIAS")
                ?: error("Release signing: keyAlias not set.")
            keyPassword = keystoreProp("keyPassword", "AI_QUOTA_RELEASE_KEY_PASSWORD")
                ?: error("Release signing: keyPassword not set.")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.nanohttpd)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
