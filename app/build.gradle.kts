plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI hər build-ə artan nömrə verir ki, yeni APK köhnənin üstünə quraşdırılsın.
val ciVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val ciVersionName = (findProperty("versionName") as String?) ?: "1.0.$ciVersionCode"

// Sabit imza açarı: signing/release.jks + signing/password.txt (və ya CI secret-ləri).
val releaseKeystore = rootProject.file("signing/release.jks")
val keystorePassword: String = System.getenv("RADAR_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: rootProject.file("signing/password.txt").takeIf { it.exists() }?.readText()?.trim()
    ?: ""
val hasStableKey = releaseKeystore.exists() && keystorePassword.isNotEmpty()

android {
    namespace = "az.novbe.radar"
    compileSdk = 35

    defaultConfig {
        applicationId = "az.novbe.radar"
        minSdk = 28
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (hasStableKey) {
            create("stable") {
                storeFile = releaseKeystore
                storePassword = keystorePassword
                keyAlias = "novbe"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasStableKey) signingConfigs.getByName("stable") else signingConfigs.getByName("debug")
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Ehtiyat yol: ekran şəkli + cihazda işləyən OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
