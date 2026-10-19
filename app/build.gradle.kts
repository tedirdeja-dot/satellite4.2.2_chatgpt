plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.satellite.wallpaper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.satellite.wallpaper"
        minSdk = 23
        targetSdk = 35
        versionCode = 23
        versionName = "4.2.2"
    }

    // Keystore fija incluida en el repo: todas las compilaciones de GitHub llevan la misma firma
    // y se pueden instalar unas encima de otras.
    signingConfigs {
        create("fixed") {
            storeFile = file("fixed.keystore")
            storePassword = "satellite"
            keyAlias = "satellite"
            keyPassword = "satellite"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
