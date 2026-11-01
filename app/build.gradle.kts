plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.pylerium.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pylerium.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.libvlc)
    testImplementation(libs.junit)
}
