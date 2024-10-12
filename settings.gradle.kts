pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "arcverseRepository"
            url = uri("https://repo.arcver.se/private")
            credentials {
                username = providers.environmentVariable("arcverseRepositoryUsername").orNull ?: ""
                password = providers.environmentVariable("arcverseRepositoryPassword").orNull ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

rootProject.name = "Xenon Store"
include(":app")