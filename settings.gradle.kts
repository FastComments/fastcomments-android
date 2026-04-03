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
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            name = "repsy"
            url = uri("https://repo.repsy.io/mvn/winrid/fastcomments")
            credentials {
                username = providers.gradleProperty("repsyUsername").getOrElse(System.getenv("REPSY_USERNAME") ?: "")
                password = providers.gradleProperty("repsyPassword").getOrElse(System.getenv("REPSY_PASSWORD") ?: "")
            }
        }
    }
}

rootProject.name = "fastcomments-example-simple"
include(":app")
include(":libraries:sdk")
