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
    }
}

rootProject.name = "VitalHubApplication"
include(":app")
include(":core:common")
include(":core:navi")
include(":core:permission")
include(":core:storage")
include(":foundation:bluetooth")
include(":provider:user")
include(":provider:collection")
include(":feature:home")
include(":feature:user")
include(":feature:questionnaire")
include(":feature:collection")
include(":feature:analysis")
