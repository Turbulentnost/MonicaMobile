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
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"${readApiBaseUrl()}\"")
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"Turbulentnost\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"MonicaMobile\"")
    }

    // Soft-update требует одну и ту же подпись, что и уже установленные APK.
    // По умолчанию — ~/.android/debug.keystore (как у monica-1.1.apk в Releases).
    // Можно переопределить файлом mobile/upload-keystore.jks + upload-keystore.properties.
    signingConfigs {
        create("upload") {
            val propsFile = rootProject.file("upload-keystore.properties")
            val projectStore = rootProject.file("upload-keystore.jks")
            val debugStore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (propsFile.exists() && projectStore.exists()) {
                val props = mutableMapOf<String, String>()
                propsFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || "=" !in trimmed) return@forEach
                    val key = trimmed.substringBefore("=").trim()
                    val value = trimmed.substringAfter("=").trim()
                    props[key] = value
                }
                storeFile = projectStore
                storePassword = props["storePassword"]
                keyAlias = props["keyAlias"]
                keyPassword = props["keyPassword"]
            } else {
                storeFile = debugStore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Важно: release, не debug — иначе Android даёт «несовместимость версий»
            // при обновлении поверх не-debuggable сборки.
            signingConfig = signingConfigs.getByName("upload")
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
