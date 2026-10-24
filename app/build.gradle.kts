plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sounote.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sounote.app"
        minSdk = 29
        targetSdk = 36
        // CI'da her derleme numarası artar, böylece yeni APK eskisinin üstüne kurulur.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "2.0.$versionCode"
        // Sadece telefon/tablet işlemcileri (emülatör x86 kodları APK'yı şişiriyordu).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // İmza anahtarı depoda DEĞİL: CI'da GitHub Secrets'tan gelir (SOUNOTE_KEYSTORE_B64 → dosya, SOUNOTE_KEY_PASSWORD).
    // Anahtar aynı kalmalı: değişirse güncelleme kurulmaz ve uygulamayı silmek (notları kaybetmek) gerekir.
    val keystorePath = System.getenv("SOUNOTE_KEYSTORE")
    val signPassword = System.getenv("SOUNOTE_KEY_PASSWORD")
    val canSign = keystorePath != null && signPassword != null && file(keystorePath).exists()
    signingConfigs {
        if (canSign) {
            create("sounote") {
                storeFile = file(keystorePath!!)
                storePassword = signPassword
                keyAlias = "sounote"
                keyPassword = signPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("sounote")
        }
        debug {
            if (canSign) signingConfig = signingConfigs.getByName("sounote")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        // Kod (dex) APK içinde sıkıştırılsın: sıkıştırılmamış dex tablete inerken bozuluyordu (CRC hatası, build-20..22).
        dex {
            useLegacyPackaging = true
        }
        // Yerel kütüphaneler de sıkıştırılsın (aynı bozulma riskine karşı, APK da küçülür).
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            // PDFBox'ın bağımlılıklarından gelen çakışan üst veri dosyaları
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.pdfbox.android)
    implementation(libs.androidx.graphics.core)
    // Yazılı notta kalemle yazılanı yazıya çevirme (Türkçe model ilk kullanımda indirilir)
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
}
