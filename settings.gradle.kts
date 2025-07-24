// # walt.id identity build configuration

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

infix fun String.whenEnabled(setting: Boolean) = if (setting) this else null
fun String.group(vararg elements: String?) = elements.map { it?.let { "$this:$it" } }.toTypedArray()

// Build setup:

// Shorthands
val libraries = ":waltid-libraries"
val applications = ":waltid-applications"
val services = ":waltid-services"

val modules = listOfNotNull(
    * "$libraries:crypto".group(
        "waltid-crypto",
        "waltid-crypto-oci",
        "waltid-crypto-aws",
        "waltid-crypto-android" whenEnabled enableAndroidBuild,
        "waltid-crypto-ios" whenEnabled enableIosBuild,
        "waltid-target-ios" whenEnabled enableIosBuild,
        "waltid-target-ios:implementation" whenEnabled enableIosBuild,
    ),

    * "$libraries:credentials".group(
        "waltid-w3c-credentials",
        "waltid-digital-credentials",
        "waltid-mdoc-credentials",
        "waltid-dif-definitions-parser",
        "waltid-dcql",
        "waltid-verification-policies",
        "waltid-holder-policies"
    ),

    * "$libraries:protocols".group(
        "waltid-openid4vc",
        "waltid-openid4vp",
    ),

    * "$libraries:sdjwt".group(
        "waltid-sdjwt",
        "waltid-sdjwt-ios" whenEnabled enableIosBuild,
    ),

    * "$libraries:auth".group(
        "waltid-ktor-authnz",
        "waltid-permissions",
        "waltid-idpkit"
    ),

    "$libraries:waltid-core-wallet",

    /*
    * "$libraries:util".group(
        "waltid-reporting"
    ),
    */

    "$libraries:waltid-did",
    "$libraries:waltid-java-compat",
    "$libraries:waltid-library-commons",

    // Service commons
    "$services:waltid-service-commons",
    "$services:waltid-service-commons-test",

    // Services based on libs
    "$services:waltid-issuer-api",
    "$services:waltid-verifier-api",
    "$services:waltid-wallet-api",

    // Service tests
    "$services:waltid-e2e-tests",

    // CLI
    "$applications:waltid-cli",

    ":waltid-applications:waltid-android" whenEnabled enableAndroidBuild,

    "$applications:waltid-openid4vc-ios-testApp" whenEnabled enableIosBuild,
    "$applications:waltid-openid4vc-ios-testApp:shared" whenEnabled enableIosBuild
)

include(*modules.toTypedArray())

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "waltid-identity"
