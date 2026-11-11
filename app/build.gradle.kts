plugins {
    id("com.android.application")
}

// Signature release : fournie par variables d'environnement (CI) – jamais dans le dépôt.
val keystorePath: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "app.mailcowlogs.viewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.mailcowlogs.viewer"
        minSdk = 26
        targetSdk = 36
        versionCode = 10001
        versionName = "1.0.1"
    }

    buildFeatures {
        buildConfig = true
    }

    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.biometric:biometric:1.1.0")
}
