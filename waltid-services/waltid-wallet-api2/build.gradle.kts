import io.ktor.plugin.features.*

object Versions {
    const val HOPLITE_VERSION = "2.9.0"
}

plugins {
    id("waltid.ktorbackend")
    id("waltid.ktordocker")
}

group = "id.walt"

dependencies {
    // The server library contains route handlers, OpenAPI docs, and the WalletResolver
    // interface. It transitively pulls in waltid-openid4vc-wallet (base library).
    // Both the OSS service and the Enterprise service depend on this library.
    api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-server"))

    // Service commons (ServiceMain, ServiceFeatureCatalog, WaltConfig, etc.)
    api(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR server (for Main.kt plugins only; routes live in the server library) -- */
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.server.sessions)
    implementation(identityLibs.ktor.server.authjwt)
    implementation(identityLibs.ktor.server.auto.head.response)
    implementation(identityLibs.ktor.server.host.common)
    implementation(identityLibs.ktor.server.compression)
    implementation(identityLibs.ktor.server.cors)
    implementation(identityLibs.ktor.server.forwarded.header)
    implementation(identityLibs.ktor.server.call.logging)
    implementation(identityLibs.ktor.server.call.id)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.server.cio)

    /* -- Serialization -- */
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    /* -- Kotlin -- */
    implementation(identityLibs.kotlinx.datetime)
    implementation(identityLibs.kotlinx.coroutines.core)

    /* -- Config -- */
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    /* -- Logging -- */
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    /* -- Auth (optional feature) -- */
    implementation(project(":waltid-libraries:auth:waltid-ktor-authnz"))

    /* -- Tests -- */
    testImplementation(identityLibs.bundles.waltid.ktortesting)
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(project(":waltid-services:waltid-service-commons-test"))
    // Integration tests: OpenID4VCI 1.0 in-process issuer + Verifier2
    testImplementation(project(":waltid-libraries:protocols:waltid-openid4vci"))
    testImplementation(project(":waltid-libraries:protocols:waltid-openid4vp-wallet"))
    testImplementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
    testImplementation(project(":waltid-libraries:credentials:waltid-holder-policies"))
    testImplementation(project(":waltid-services:waltid-verifier-api2"))
    testImplementation(identityLibs.ktor.server.sse)
}

application {
    mainClass.set("id.walt.wallet2.MainKt")
}

buildConfig {
    packageName("id.walt.wallet2")
}

ktor {
    docker {
        portMappings.set(listOf(DockerPortMapping(4000, 4000, DockerPortMappingProtocol.TCP)))
    }
}
