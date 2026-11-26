plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
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

        // Firebase Realtime Database — URL diambil dari gradle.properties.
        // Nilai default bisa diganti sesuai region database di Firebase Console.
        val databaseUrl = (findProperty("firebaseDatabaseUrl") as String?)
            ?: "https://mynotes-30b82-default-rtdb.firebaseio.com"
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"$databaseUrl\"")

        // OAuth client untuk Google Sign-In (client_type = 3 di google-services.json).
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"869732787640-7344plhfpbitl0qkpk4ociaf9vo682um.apps.googleusercontent.com\""
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

    /* ---- Akun & sinkronisasi (Firebase) ---- */
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Google Sign-In lewat Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
