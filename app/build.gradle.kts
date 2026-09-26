plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase (аккаунты, админка). Без google-services.json приложение соберётся, но вход будет недоступен.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// -PwithLlama=false: собрать без нативного движка llama.cpp (быстрее, ИИ-чат будет недоступен)
val withLlama: Boolean = (project.findProperty("withLlama") as String?)?.toBoolean() ?: true
// -PwithVision=false: без зрения (mtmd); -PllamaTag=b6550: другая версия llama.cpp
val withVision: Boolean = (project.findProperty("withVision") as String?)?.toBoolean() ?: true
val llamaTag: String = (project.findProperty("llamaTag") as String?) ?: "b8720"

// Ключ подписи больше не хранится в репозитории.
// CI берёт его из GitHub Secrets. Локально: положить schoolhub.jks в app/ и задать переменные окружения.
val signingStoreFile = file(System.getenv("SIGNING_STORE_FILE") ?: "schoolhub.jks")
val signingStorePassword: String? = System.getenv("SIGNING_STORE_PASSWORD")
val signingKeyAlias: String? = System.getenv("SIGNING_KEY_ALIAS")
val signingKeyPassword: String? = System.getenv("SIGNING_KEY_PASSWORD")
val hasReleaseKey = signingStoreFile.exists() && !signingStorePassword.isNullOrEmpty() &&
    !signingKeyAlias.isNullOrEmpty() && !signingKeyPassword.isNullOrEmpty()

android {
    namespace = "com.school.hub"
    compileSdk = 34
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.school.hub"
        // 26 (Android 8.0): java.time, адаптивные иконки; ML Kit 21+, Nearby 16+,
        // llama.cpp: arm64 с NEON (на практике Android 8+).
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "2.1.0"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("boolean", "WITH_LLAMA", withLlama.toString())
        buildConfigField("boolean", "WITH_VISION", (withLlama && withVision).toString())
        buildConfigField("String", "LLAMA_TAG", "\"$llamaTag\"")
        ndk { abiFilters += listOf("arm64-v8a") }
        if (withLlama) {
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DLLAMA_TAG=$llamaTag",
                        "-DSCHOOLHUB_VISION=" + (if (withVision) "ON" else "OFF"),
                    )
                }
            }
        }
    }

    if (withLlama) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    // Постоянный ключ подписи: SHA-1 не меняется между сборками (нужно для входа через Google
    // и чтобы новые версии ставились поверх старых без потери данных).
    signingConfigs {
        if (hasReleaseKey) {
            create("parta") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("parta") ?: signingConfigs.getByName("debug")
        debug { signingConfig = signing }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }
    // Release-сборка не должна падать из-за предупреждений lint.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")
    // Guava ListenableFuture: нужен CameraX (PreviewView / ProcessCameraProvider)
    implementation("com.google.guava:guava:32.1.2-android")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Сеть
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Бесплатная синхронизация через MQTT-брокер
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // P2P без интернета
    implementation("com.google.android.gms:play-services-nearby:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Офлайн-переводчик + распознавание текста
    implementation("com.google.mlkit:translate:17.0.2")
    implementation("com.google.mlkit:language-id:17.0.4")
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Камера
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Картинки
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Firebase: аккаунты + облачная база (офлайн-кэш и очередь записи встроены)
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Вход через Google (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.code.gson:gson:2.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
