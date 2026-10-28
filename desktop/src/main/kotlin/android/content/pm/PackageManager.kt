package android.content.pm

class PackageInfo(val versionName: String?)

class PackageManager {
    /** jpackage deja la versión en esta propiedad; sin instalar (desde Gradle) no existe. */
    fun getPackageInfo(name: String, flags: Int) = PackageInfo(System.getProperty("jpackage.app-version") ?: "1.0")

    companion object {
        const val PERMISSION_GRANTED = 0
    }
}
