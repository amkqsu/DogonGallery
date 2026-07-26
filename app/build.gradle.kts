plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "lol.dogon.gallery"
    compileSdk = 34

    defaultConfig {
        applicationId = "lol.dogon.gallery"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    // Jetpack Compose'u aktif ediyoruz
    buildFeatures {
        compose = true
    }
    
        composeOptions {
        // 1.5.1 olan değeri 1.5.8 olarak değiştiriyoruz
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Security Crypto
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // ML Kit
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // await() fonksiyonu (Task'ler) için Coroutines Play Services desteği
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
