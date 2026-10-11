pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "dependency-ownership"
include(":app", ":hilt-app", ":broken", ":cart-api", ":cart-impl",
    ":session-api", ":session-impl", ":legacy-bridge", ":feature-ui", ":lease-lab")
