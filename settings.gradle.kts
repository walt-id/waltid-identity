// # walt.id identity build configuration

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

// Build setup:

// Shorthands
val libraries = ":waltid-libraries"
val applications = ":waltid-applications"
val services = ":waltid-services"

val baseModules = listOf(
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
    "$libraries:waltid-credentials-base",

    "$libraries:waltid-java-compat"
)

val androidModules = listOf(
    ":waltid-libraries:waltid-crypto-android",
    ":waltid-applications:waltid-android"
)

val iosModules = listOf(
    "$libraries:waltid-crypto-ios",
    "$libraries:waltid-sdjwt-ios",
    "$libraries:waltid-target-ios",
    "$libraries:waltid-target-ios:implementation",
    "$applications:waltid-openid4vc-ios-testApp",
    "$applications:waltid-openid4vc-ios-testApp:shared"
)

val enabledModules = ArrayList<String>(baseModules)

if (enableAndroidBuild) enabledModules.addAll(androidModules)
if (enableIosBuild) enabledModules.addAll(iosModules)

include(*enabledModules.toTypedArray())

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "waltid-identity"
