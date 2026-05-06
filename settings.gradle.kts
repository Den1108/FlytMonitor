pluginManagement {
    repositories {
        google()           // Важно для Android плагинов
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()           // Важно для библиотек Android (AppCompat, Material)
        mavenCentral()
    }
}

rootProject.name = "FlytMonitor"
include(":app") // Двоеточие перед app — это стандарт для модулей Android