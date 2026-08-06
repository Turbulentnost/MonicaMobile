plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

fun readApiBaseUrl(): String {
    val localFile = rootProject.file("local.properties")
    val fallback = "https://metamonica.ru"
    // Старый VPS — больше не наш; домен metamonica.ru смотрит на 159.194.229.101.
    val retiredHosts = listOf("159.194.232.74")
    if (!localFile.exists()) return fallback
    // Читаем без java.util.Properties — в AGP `java` уже занят
    val raw = localFile.readLines()
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("monica.api.base.url=") }
        ?.substringAfter("=", missingDelimiterValue = "")
        ?.trim()
        // local.properties может экранировать ':' как '\:'.
        // В BuildConfig обратный слеш превращается в illegal escape.
        ?.replace("\\:", ":")
        ?.trimEnd('/')
        ?.takeIf { it.isNotEmpty() }
        ?: fallback
    if (retiredHosts.any { raw.contains(it) }) {
        logger.warn(
            "monica.api.base.url указывает на старый сервер ($raw). " +
                "Используем $fallback",
        )
        return fallback
    }
    return raw
}

android {
    namespace = "com.example.monica"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chat.monica"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"${readApiBaseUrl()}\"")
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"Turbulentnost\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"MonicaMobile\"")
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
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.media3:media3-common:1.6.1")

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
