plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val numeroBuild = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.vidro.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vidro.launcher"
        minSdk = 33 // shaders AGSL exigem Android 13+
        targetSdk = 35
        versionCode = numeroBuild
        versionName = "0.1.$numeroBuild"
    }

    // Chave fixa: assim cada APK novo instala por cima do anterior sem desinstalar.
    signingConfigs {
        create("fixa") {
            storeFile = file("../keystore/vidro.jks")
            storePassword = "vidro123"
            keyAlias = "vidro"
            keyPassword = "vidro123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixa")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
