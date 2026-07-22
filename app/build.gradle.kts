plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

fun readApiBaseUrl(): String {
    val localFile = rootProject.file("local.properties")
    val fallback = "https://metamonica.ru"
    if (!localFile.exists()) return fallback
    // Читаем без java.util.Properties — в AGP `java` уже занят
    return localFile.readLines()
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("monica.api.base.url=") }
        ?.substringAfter("=", missingDelimiterValue = "")
        ?.trim()
        // local.properties может экранировать ':' как '\:'.
        // В BuildConfig обратный слеш превращается в illegal escape.
        ?.replace("\\:", ":")
        ?.takeIf { it.isNotEmpty() }
        ?: fallback
}

android {
    namespace = "com.example.monica"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chat.monica"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"${readApiBaseUrl()}\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    // Прямая координата: alias libs.webrtc.sdk иногда не генерируется в Version Catalog
    implementation("io.github.webrtc-sdk:android:144.7559.09")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
