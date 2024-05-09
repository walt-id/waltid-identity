pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

rootProject.name = "waltid-identity"
include(
    // Base SSI libs
    "waltid-crypto",
    "waltid-did",
    "waltid-verifiable-credentials",
    "waltid-mdoc-credentials",
    "waltid-sdjwt",

    // Protocols
    "waltid-openid4vc",

    // Services based on libs
    "waltid-issuer-api",
    "waltid-verifier-api",
    "waltid-wallet-api",

    // CLI
    "waltid-cli",

    // Reporting
    "waltid-reporting",

    // Samples
    //"waltid-android"
)

