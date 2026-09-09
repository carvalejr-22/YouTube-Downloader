plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.carlosvale.ytdownloader"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.carlosvale.ytdownloader"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.3.2"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // O motor Python/FFmpeg representa a maior parte do APK. Gerar um APK
    // por arquitetura evita instalar binários de processadores que o aparelho
    // nunca utilizará. O universal continua disponível para compatibilidade.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
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
        }
    }

    packaging {
        jniLibs {
            // As bibliotecas do yt-dlp-android distribuem Python/FFmpeg como
            // payloads .zip.so; eles precisam permanecer intactos.
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libffmpeg.zip.so", "**/libpython.zip.so")
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Não há @Preview no projeto; ui-tooling/ui-tooling-preview só aumentavam
    // o APK de teste e não participavam da execução do GetMuvi.
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
