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

val libraries = ":waltid-libraries"
val applications = ":waltid-applications"
val services = ":waltid-services"
include(
    // Base SSI libs
    "$libraries:waltid-crypto",
    "$libraries:waltid-did",
    "$libraries:waltid-verifiable-credentials",
    "$libraries:waltid-mdoc-credentials",
    "$libraries:waltid-sdjwt",

    // Protocols
    "$libraries:waltid-openid4vc",

    // Service commons
    "$services:waltid-service-commons",

    // Services based on libs
    "$services:waltid-issuer-api",
    "$services:waltid-verifier-api",
    "$services:waltid-wallet-api",

    // Service tests
    "$services:waltid-e2e-tests",

    // CLI
    "$applications:waltid-cli",

    // Reporting
    "$libraries:waltid-reporting",

    // OCI extension for waltid-crypto
    "$libraries:waltid-crypto-oci",

    // Android - uncomment to enable build:
    /*
    ":waltid-libraries:waltid-crypto-android",
    ":waltid-applications:waltid-android"
    */

    // iOS - uncomment to enable build:
    /*
    "$libraries:waltid-crypto-ios",
    "$libraries:waltid-target-ios",
    "$libraries:waltid-target-ios:implementation",
    "$applications:waltid-openid4vc-ios-testApp",
    "$applications:waltid-openid4vc-ios-testApp:shared"
     */
)
//include("waltid-e2e-tests")