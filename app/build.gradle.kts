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

    // Release signing: the key stays off GitHub. Set PYLERIUM_KEYSTORE, PYLERIUM_KEYSTORE_PASSWORD,
    // PYLERIUM_KEY_ALIAS and PYLERIUM_KEY_PASSWORD to build a signed release; without them the release is unsigned.
    signingConfigs {
        create("release") {
            System.getenv("PYLERIUM_KEYSTORE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("PYLERIUM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PYLERIUM_KEY_ALIAS")
                keyPassword = System.getenv("PYLERIUM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("PYLERIUM_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
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
