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
        "waltid-crypto-azure",
        "waltid-crypto-android" whenEnabled enableAndroidBuild,
        "waltid-crypto-ios" whenEnabled enableIosBuild,
        "waltid-target-ios" whenEnabled enableIosBuild,
        "waltid-target-ios:implementation" whenEnabled enableIosBuild,
        "waltid-crypto2",
        "waltid-cose",
        "waltid-x509",
    ),

    * "$libraries:credentials".group(
        "waltid-w3c-credentials",
        "waltid-digital-credentials",
        "waltid-digital-credentials-examples",
        "waltid-mdoc-credentials",
        "waltid-dif-definitions-parser",
        "waltid-dcql",
        "waltid-verification-policies",
        "waltid-verification-policies2",
        "waltid-verification-policies2-vp",
        "waltid-holder-policies",
        "waltid-vical",
        "waltid-mdoc-credentials2"
    ),

    * "$libraries:protocols".group(
        "waltid-openid4vc",
        "waltid-openid4vci",
        "waltid-openid4vp",
        "waltid-openid4vp-verifier",
        "waltid-openid4vp-verifier-openapi",
        "waltid-openid4vp-clientidprefix",
        "waltid-openid4vp-wallet"
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

    * "$libraries:web".group(
        "waltid-ktor-notifications",
        "waltid-ktor-notifications-core",
        "waltid-web-data-fetching"
    ),

    "$libraries:waltid-core-wallet",

    "$libraries:waltid-did",
    "$libraries:waltid-java-compat",
    "$libraries:waltid-library-commons",

    // Service commons
    "$services:waltid-service-commons",
    "$services:waltid-service-commons-test",

    // Services based on libs
    "$services:waltid-issuer-api",
    "$services:waltid-verifier-api",
    "$services:waltid-verifier-api2",
    "$services:waltid-wallet-api",

    // Service tests
    "$services:waltid-e2e-tests",
    "$services:waltid-integration-tests",
    "$services:waltid-openid4vp-conformance-runners",

    // CLI
    "$applications:waltid-cli",

    ":waltid-applications:waltid-android" whenEnabled enableAndroidBuild,

    "$applications:waltid-openid4vc-ios-testApp" whenEnabled enableIosBuild,
    "$applications:waltid-openid4vc-ios-testApp:shared" whenEnabled enableIosBuild
)

include(*modules.toTypedArray())

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)

    repositories {
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
        mavenCentral()
        google()
    }

    versionCatalogs {
        create("identityLibs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "waltid-identity"