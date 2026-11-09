plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nova.keyboard"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nova.keyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "2").toInt() + 100
        versionName = "5.1"
    }
    signingConfigs {
        create("nova") {
            val ks = System.getenv("KS_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KS_PASS")
                keyAlias = "nova"
                keyPassword = System.getenv("KS_PASS")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (System.getenv("KS_FILE") != null) signingConfig = signingConfigs.getByName("nova")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
