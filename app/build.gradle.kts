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
        versionCode = 14
        versionName = "2.3"

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
        debug {
            // Memory experiment switch (PLANS/03 T1/T2): clear WebView's resource cache at the end
            // of each collection cycle. "none" (default) | "ram" = clearCache(false) | "disk" =
            // clearCache(true). Pass -PcacheClearExperiment=ram when building a measurement APK.
            val mode = (project.findProperty("cacheClearExperiment") as String?) ?: "none"
            require(mode in setOf("none", "ram", "disk")) { "cacheClearExperiment must be none, ram or disk" }
            buildConfigField("String", "CACHE_CLEAR_EXPERIMENT", "\"$mode\"")
            // Renderer recycling PoC (PLANS/03 T6): terminate the WebView renderer once per
            // collection cycle. Off unless built with -ProutineRendererRecycle=true.
            val recycle = (project.findProperty("routineRendererRecycle") as String?) ?: "false"
            require(recycle in setOf("true", "false")) { "routineRendererRecycle must be true or false" }
            buildConfigField("boolean", "ROUTINE_RENDERER_RECYCLE", recycle)
        }
        release {
            buildConfigField("String", "CACHE_CLEAR_EXPERIMENT", "\"none\"")
            buildConfigField("boolean", "ROUTINE_RENDERER_RECYCLE", "false")
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
        // BuildConfig.VERSION_NAME is shown in the dashboard header
        buildConfig = true
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
