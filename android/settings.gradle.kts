// IT Részvény Monitor — natív Android kliens (TERV-ANDROID.md)
// Önálló Gradle-projekt: Android Studio-ban közvetlenül megnyitható (File → Open → android/).

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MPAndroidChart kizárólag JitPackről érhető el — a content-szűrő miatt
        // más függőség nem oldódhat fel innen.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.PhilJay") }
        }
    }
}

rootProject.name = "reszveny-monitor"
include(":app")
