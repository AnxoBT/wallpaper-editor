import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.desktop)
}

/**
 * Versión de Windows. Compila el mismo código que la app de Android (editor, procesado, etiquetas,
 * selector y ajustes): lo propio de Android lo aportan las clases de desktop/src/main/kotlin, con
 * los mismos nombres. Aquí se excluye lo que no tiene sentido en un PC o se sustituye por otra versión.
 */
val appJava = file("../app/src/main/java")
val appRes = file("../app/src/main/res")
val desktopRes = file("src/main/res")
val generatedR = layout.buildDirectory.dir("generated/r")

val desktopReplaced = listOf(
    "MainActivity.kt",
    "processing/AiInpainter.kt",
    "processing/TextDetector.kt",
    "processing/ImageLoader.kt",
    "storage/GallerySaver.kt",
    "storage/OriginalReplacer.kt",
    "storage/OpenInGallery.kt",
    "storage/WallpaperSetter.kt",
    "ui/DeviceScreen.kt",
    "ui/Platform.kt",
    "ui/settings/AppLanguage.kt",
    "ui/wallpapers/WallpapersScreen.kt",
    "ui/wallpapers/WallpapersViewModel.kt",
    "wallpaper/ClockBitmap.kt",
    "wallpaper/ClockLayouts.kt",
    "wallpaper/ClockWidget.kt",
    "wallpaper/LockClockFont.kt",
    "wallpaper/SlideshowWallpaperService.kt",
    "wallpaper/VivoLockColor.kt",
    "wallpaper/WallpaperController.kt",
    "wallpaper/WallpaperRenderer.kt",
).map { "com/wallpaperfit/app/$it" }

/** La clase R de Android, sacada de los mismos recursos (más los textos propios de esta versión). */
val generateR by tasks.registering {
    val outputDir = generatedR
    inputs.dir(appRes)
    inputs.dir(desktopRes)
    outputs.dir(outputDir)
    doLast {
        val kinds = sortedMapOf<String, MutableSet<String>>()
        fun add(kind: String, name: String) = kinds.getOrPut(kind) { sortedSetOf() }.add(name)
        listOf(appRes, desktopRes).forEach { root ->
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val folder = file.parentFile.name
                if (folder.startsWith("values")) {
                    Regex("<(string|plurals) name=\"(\\w+)\"").findAll(file.readText()).forEach { add(it.groupValues[1], it.groupValues[2]) }
                } else if (folder in listOf("drawable", "raw", "font")) {
                    add(folder, file.nameWithoutExtension)
                }
            }
        }
        var next = 1
        val names = ArrayList<String>()
        val body = kinds.entries.joinToString("\n") { (kind, items) ->
            val fields = items.joinToString("\n") { name ->
                names += "$kind/$name"
                "        const val $name = ${next++}"
            }
            "    object $kind {\n$fields\n    }"
        }
        val list = names.joinToString(",\n") { "        \"$it\"" }
        val target = outputDir.get().file("com/wallpaperfit/app/R.kt").asFile
        target.parentFile.mkdirs()
        target.writeText("package com.wallpaperfit.app\n\nobject R {\n$body\n\n    val names = listOf(\n        \"\",\n$list,\n    )\n}\n")
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateR)
        kotlin.srcDir(appJava)
        kotlin.exclude(desktopReplaced)
    }
}

tasks.processResources {
    // Los recursos de Android, y encima los textos propios de esta versión.
    from(appRes) {
        include("values*/strings*.xml", "raw/**", "drawable/ic_launcher_foreground.xml")
        into("res")
    }
    from(desktopRes) { into("res") }
    from("../docs/icon.png") { into("res") }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    implementation(compose.desktop.windows_x64)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.desktop.lifecycle.viewmodel.compose)
    implementation(libs.desktop.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.org.json)
    implementation(libs.jna)

    testImplementation("junit:junit:4.13.2")
    testImplementation(compose.desktop.uiTestJUnit4)
}

kotlin.compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

compose.desktop {
    application {
        mainClass = "com.wallpaperfit.app.desktop.MainKt"
        jvmArgs += listOf("-Xmx4g", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "WallpaperEditor"
            packageVersion = "1.0.0"
            description = "Wallpaper Editor"
            vendor = "AnxoBT"
            modules("java.desktop", "java.logging", "java.prefs", "jdk.unsupported")
            windows {
                iconFile.set(project.file("src/main/packaging/icon.ico"))
                menu = true
                menuGroup = "Wallpaper Editor"
                shortcut = true
                perUserInstall = true
                dirChooser = false
                upgradeUuid = "a6a0f1f4-7f3c-4b5e-9c0a-1d2e3f405162"
            }
        }
    }
}
