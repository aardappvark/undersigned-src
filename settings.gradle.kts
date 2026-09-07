pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral(); google() }
}
rootProject.name = "Undersigned"
include(":protocol")
// ":app" (Android shell) is bootstrapped from the fleet scaffold once the owner fixes the name — see README.
