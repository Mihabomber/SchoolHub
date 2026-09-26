pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // JitPack нужен для Tesseract4Android (распознавание кириллицы на фото)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "SchoolHub"
include(":app")

// Tesseract для русского текста на фото: ML Kit text-recognition умеет только латиницу.
// Подключаем здесь, чтобы не трогать app/build.gradle.kts.
gradle.beforeProject {
    val p = this
    if (p.path == ":app") {
        p.pluginManager.withPlugin("com.android.application") {
            p.dependencies.add("implementation", "cz.adaptech.tesseract4android:tesseract4android:4.8.0")
            // Если и llama.cpp, и Tesseract кладут libc++_shared.so, берём одну копию.
            runCatching {
                val android = p.extensions.getByName("android")
                val packaging = android.javaClass.getMethod("getPackaging").invoke(android)
                val jni = packaging.javaClass.getMethod("getJniLibs").invoke(packaging)
                @Suppress("UNCHECKED_CAST")
                (jni.javaClass.getMethod("getPickFirsts").invoke(jni) as MutableSet<String>).add("**/libc++_shared.so")
            }
        }
    }
}
