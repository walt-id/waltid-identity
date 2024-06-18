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
    ":waltid-libraries:waltid-crypto",
    ":waltid-libraries:waltid-did",
    ":waltid-libraries:waltid-verifiable-credentials",
    ":waltid-libraries:waltid-mdoc-credentials",
    ":waltid-libraries:waltid-sdjwt",

    // Protocols
    ":waltid-libraries:waltid-openid4vc",

    // Service commons
    ":waltid-services:waltid-service-commons",

    // Services based on libs
    ":waltid-services:waltid-issuer-api",
    ":waltid-services:waltid-verifier-api",
    ":waltid-services:waltid-wallet-api",

    // Service tests
    ":waltid-services:waltid-e2e-tests",

    // CLI
    ":waltid-applications:waltid-cli",

    // Reporting
    ":waltid-libraries:waltid-reporting",

    // OCI extension for waltid-crypto
    ":waltid-libraries:waltid-crypto-oci",

    // Android - uncomment to enable build:
    /*
    ":waltid-libraries:waltid-crypto-android",
    ":waltid-applications:waltid-android"
    */
)
//include("waltid-e2e-tests")
