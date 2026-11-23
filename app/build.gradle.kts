plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uz.aladdin.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "uz.aladdin.assistant"
        minSdk = 26
        targetSdk = 34
        // Har bir GitHub yig'ishida versiya raqami o'sib boradi (eski ustidan yangilash uchun)
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    // Doimiy imzo kaliti: barcha versiyalar bir xil imzolanadi,
    // shuning uchun yangi APK eskisi ustidan o'rnatiladi va sozlamalar saqlanib qoladi.
    // Kalit repozitoriyda emas — GitHub Secrets'dan (ALADDIN_KEYSTORE) olinadi.
    val keyFile = file("aladdin.p12")
    val keyPass = System.getenv("ALADDIN_KEY_PASSWORD") ?: "aladdin2026"
    signingConfigs {
        create("aladdin") {
            storeFile = keyFile
            storeType = "pkcs12"
            storePassword = keyPass
            keyAlias = "aladdin"
            keyPassword = keyPass
        }
    }

    buildTypes {
        debug {
            if (keyFile.exists()) signingConfig = signingConfigs.getByName("aladdin")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (keyFile.exists()) signingConfigs.getByName("aladdin") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
