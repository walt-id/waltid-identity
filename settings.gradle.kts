// # walt.id identity build configuration


// --- Custom build flags ---

/** For building Android, make sure to set up your Android SDK in local.properties */
ext["enable-android-build"] = false
/** For iOS builds, run `kdoctor` (https://github.com/Kotlin/kdoctor) to make sure your environment is setup correctly */
ext["enable-ios-build"] = false

// --- End of custom build flags ---


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
)

val androidModules = listOf(
    ":waltid-libraries:waltid-crypto-android",
    ":waltid-applications:waltid-android"
)

val iosModules = listOf(
    "$libraries:waltid-crypto-ios",
    "$libraries:waltid-target-ios",
    "$libraries:waltid-target-ios:implementation",
    "$applications:waltid-openid4vc-ios-testApp",
    "$applications:waltid-openid4vc-ios-testApp:shared"
)

val enabledModules = ArrayList<String>(baseModules)

if (ext["enable-android-build"] == true) enabledModules.addAll(androidModules)
if (ext["enable-ios-build"] == true) enabledModules.addAll(iosModules)

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
