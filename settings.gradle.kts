pluginManagement {
    repositories {
        google()
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
    }
}

rootProject.name = "SniffSniff"
include(
    ":app",
    ":core:common",
    ":core:database",
    ":core:designsystem",
    ":feature:dashboard",
    ":feature:connections",
    ":feature:inspector",
    ":feature:applications",
    ":feature:settings",
    ":network:vpn",
    ":network:capture",
    ":network:forwarding",
    ":network:parser",
    ":network:flow",
    ":network:pcap",
)
