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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.ybq") }
        }
    }
}

rootProject.name = "VitalHubApplication"
include(":app")
include(":core:common")
include(":core:navi")
include(":core:permission")
include(":core:storage")
include(":foundation:bluetooth")
include(":foundation:device-api")
include(":foundation:device-transport")
include(":foundation:device-protocol")
include(":foundation:device-command")
include(":foundation:device-storage")
include(":foundation:device-waveform")
include(":foundation:device-waveform-ui")
include(":foundation:device-sdk")
include(":provider:user")
include(":provider:collection")
include(":provider:device")
include(":feature:home")
include(":feature:user")
include(":feature:questionnaire")
include(":feature:collection")
include(":feature:analysis")
include(":debug:dokit-bluetooth")
include(":debug:dokit-protocol")
include(":debug:dokit-waveform")
