plugins {
    id("com.android.application") version "8.2.2"
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "com.flyt.monitor" // Убедись, что это совпадает с пакетом в MainActivity
    compileSdk = 34

    defaultConfig {
        applicationId = "com.flyt.monitor"
        minSdk = 24 // Поддержка Android 7.0 и выше
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    // Основные библиотеки Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Библиотека для сетевых запросов (упрощает работу с HTTP)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Библиотека для фоновых задач (чтобы приложение не зависало при проверке серверов)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Тестирование
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("junit:junit:4.13.2")
}