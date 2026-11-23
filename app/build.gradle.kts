plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.catatan.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.catatan.app"
        minSdk = 26
        targetSdk = 34
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"

        // Dipakai fitur "Periksa pembaruan" (GitHub Releases)
        buildConfigField("String", "GITHUB_REPO", "\"Syuhadak27/MyNote\"")
        buildConfigField(
            "String",
            "UPDATE_URL",
            "\"https://api.github.com/repos/Syuhadak27/MyNote/releases/latest\""
        )
    }

    signingConfigs {
        // Identitas app — sengaja disimpan di repo (untuk pembelajaran).
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = "12345678"
            keyAlias = "appkey"
            keyPassword = "12345678"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Penyimpanan data (JSON, mirip IndexedDB pada versi web)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Parsing HTML catatan (impor backup dari versi web)
    implementation("org.jsoup:jsoup:1.18.1")

    // Gambar (thumbnail, lampiran)
    implementation("io.coil-kt:coil-compose:2.7.0")
}
