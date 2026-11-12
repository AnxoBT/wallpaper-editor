import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.wallpaperfit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wallpaperfit.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Solo móviles ARM: las librerías nativas de IA y OCR para x86 duplicarían el tamaño del APK.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Clave propia de Wallpaper Editor (fuera del repo: ~/.android/wallpapereditor-release.jks; datos en
    // keystore.properties, ignorado por git). Todas las versiones van firmadas con ella para que se instalen
    // encima de la anterior. Sin ese archivo (otro PC) la release se para en vez de firmar con la de depuración.
    val claves = rootProject.file("keystore.properties")
    signingConfigs {
        if (claves.exists()) {
            create("wallpapereditor") {
                val p = Properties().apply { claves.inputStream().use { load(it) } }
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }
    gradle.taskGraph.whenReady {
        if (!claves.exists() && allTasks.any { it.name.contains("Release") }) {
            throw GradleException(
                "Falta keystore.properties (clave de firma ~/.android/wallpapereditor-release.jks): " +
                    "sin ella el APK no se instala encima del anterior",
            )
        }
    }

    buildTypes {
        release {
            if (claves.exists()) signingConfig = signingConfigs.getByName("wallpapereditor")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // El modelo de IA se lee mapeado en memoria directamente desde el APK.
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.korean)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation("junit:junit:4.13.2")
}

// Estilo de Kotlin: "gradle ktlintCheck" revisa y "gradle ktlintFormat" corrige lo automático.
// Las reglas están en .editorconfig. Solo informa: no rompe la compilación.
ktlint {
    android.set(true)
    ignoreFailures.set(true)
}
