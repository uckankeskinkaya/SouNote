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
        versionName = "0.1.$versionCode"
    }

    // Kişisel uygulama: sabit anahtar repoda. Anahtar değişirse güncelleme kurulmaz
    // ve uygulamayı silmek (notları kaybetmek) gerekir, o yüzden bu dosyayı değiştirme.
    signingConfigs {
        create("sounote") {
            storeFile = rootProject.file("keystore/sounote.jks")
            storePassword = "sounote"
            keyAlias = "sounote"
            keyPassword = "sounote"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sounote")
        }
        debug {
            signingConfig = signingConfigs.getByName("sounote")
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
}
