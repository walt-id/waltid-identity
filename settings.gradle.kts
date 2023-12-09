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
    // Base SSI libs
    "waltid-crypto",
    "waltid-did",
    "waltid-verifiable-credentials",
    "waltid-mdoc-credentials",
    "waltid-sdjwt",
    "waltid-mdoc-credentials",

    // Protocols
    "waltid-openid4vc",

    // Services based on libs
    "waltid-issuer",
    "waltid-verifier",
    "waltid-wallet",

    // CLI
    "waltid-cli",

    // Reporting
    "waltid-reporting"
)
