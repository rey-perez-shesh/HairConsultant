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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Isolated DeepAR curtains test only — does not replace SceneView / MediaPipe AR.
        maven {
            url = uri("https://sdk.developer.deepar.ai/maven-android-repository/releases/")
        }
    }
}

rootProject.name = "HairConsultant"
include(":app")
 