// # walt.id identity build configuration

import java.util.Properties

fun properties(path: String) = file(path)
    .takeIf { it.isFile }
    ?.inputStream()
    ?.use { Properties().apply { load(it) } }
    ?: Properties()

val localProperties = properties("local.properties")

fun setting(name: String) =
    (startParameter.projectProperties[name]
        ?: localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: "false")
        .toBoolean()

val enableAndroidBuild = setting("enableAndroidBuild")
val enableIosBuild = setting("enableIosBuild")
val enableWalletDemoComposeWeb = setting("enableWalletDemoComposeWeb")
val enableWalletDemoCompose = enableAndroidBuild || enableIosBuild || enableWalletDemoComposeWeb

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
        "waltid-crypto2",
        "waltid-cose",
        "waltid-x509",
    ),

    * "$libraries:credentials".group(
        "waltid-w3c-credentials",
        "waltid-credential-key-resolver",
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
        "waltid-mdoc-credentials2",
        "waltid-trust-registry"
    ),

    * "$libraries:protocols".group(
        "waltid-openid4vc",
        "waltid-openid4vci",
        "waltid-openid4vci-wallet",
        "waltid-openid4vp",
        "waltid-openid4vp-verifier",
        "waltid-openid4vp-verifier-openapi",
        "waltid-openid4vp-clientidprefix",
        "waltid-openid4vp-wallet",
        "waltid-18013-7-verifier",
        "waltid-openid4vc-wallet",
        "waltid-openid4vc-wallet-persistence-mobile" whenEnabled enableAndroidBuild,
        "waltid-openid4vc-wallet-persistence-mobile" whenEnabled enableIosBuild,
        "waltid-openid4vc-wallet-persistence-server",
        "waltid-openid4vc-wallet-mobile" whenEnabled enableAndroidBuild,
        "waltid-openid4vc-wallet-mobile" whenEnabled enableIosBuild,
        "waltid-openid4vc-wallet-server",
        "waltid-mobile-test-utils" whenEnabled enableAndroidBuild,
    ),

    * "$libraries:sdjwt".group(
        "waltid-sdjwt",
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
    "$services:waltid-issuer-api2",
    "$services:waltid-verifier-api",
    "$services:waltid-verifier-api2",
    "$services:waltid-wallet-api",
    "$services:waltid-wallet-api2",
    "$services:waltid-wallet-migration",

    "$services:waltid-etsi-plugtest-cli",

    // Service tests
    "$services:waltid-e2e-tests",
    "$services:waltid-integration-tests",
    "$services:waltid-openid4vp-conformance-runners",

    // ETSI Plugtest CLI
    "$services:waltid-etsi-plugtest-cli",

    // CLI
    "$applications:waltid-cli",

    ":waltid-applications:waltid-android" whenEnabled enableAndroidBuild,
    "$applications:waltid-wallet-demo-compose:sharedLogic" whenEnabled enableWalletDemoCompose,
    "$applications:waltid-wallet-demo-compose:sharedUI" whenEnabled enableWalletDemoCompose,
    "$applications:waltid-wallet-demo-compose:androidApp" whenEnabled enableAndroidBuild,
    "$applications:waltid-wallet-demo-compose:iosApp" whenEnabled enableIosBuild,
    "$applications:waltid-wallet-demo-compose:webApp" whenEnabled enableWalletDemoComposeWeb,

    "$applications:waltid-wallet-demo-ios" whenEnabled enableIosBuild,
    "$applications:waltid-wallet-demo-ios:shared" whenEnabled enableIosBuild
)

include(*modules.distinct().toTypedArray())

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
