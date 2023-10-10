pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "waltid-identity"
include(
    // Base libs
    "waltid-crypto",
    "waltid-did",
    "waltid-credentials",

    // Services based on libs
    "waltid-issuer",
    "waltid-verifier",
)
