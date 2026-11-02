plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.pitanie.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.pitanie.app"

        minSdk = 26
        targetSdk = 28

        versionCode = 3
        versionName = "2.0-offline"
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

    // Офлайн-распознавание русских чеков
    implementation(
        "cz.adaptech.tesseract4android:tesseract4android:4.6.0"
    )

    // Работа с камерой и файлами
    implementation(
        "androidx.core:core:1.13.1"
    )
}
