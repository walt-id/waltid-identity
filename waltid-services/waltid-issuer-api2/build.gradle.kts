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
    api(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    // Ktor server
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.server.sessions)
    implementation(identityLibs.ktor.server.authjwt)
    implementation(identityLibs.ktor.server.auto.head.response)
    implementation(identityLibs.ktor.server.double.receive)
    implementation(identityLibs.ktor.server.host.common)
    implementation(identityLibs.ktor.server.status.pages)
    implementation(identityLibs.ktor.server.compression)
    implementation(identityLibs.ktor.server.cors)
    implementation(identityLibs.ktor.server.forwarded.header)
    implementation(identityLibs.ktor.server.call.logging)
    implementation(identityLibs.ktor.server.call.id)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.server.cio)
    implementation(identityLibs.ktor.server.sse)

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.serialization)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.json)
    implementation(identityLibs.ktor.client.okhttp)
    implementation(identityLibs.ktor.client.logging)


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    // Date
    implementation(identityLibs.kotlinx.datetime)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(identityLibs.kotlintest)
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(identityLibs.bundles.waltid.ktortesting)
    testImplementation(project(":waltid-services:waltid-service-commons-test"))

    // OpenID4VCI 1.0 library
    api(project(":waltid-libraries:protocols:waltid-openid4vci"))

    // walt.id crypto and DID
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-azure"))
    api(project(":waltid-libraries:waltid-did"))

    // Credentials
    api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
    api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
    api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))

    // Notifications
    implementation(project(":waltid-libraries:web:waltid-ktor-notifications"))

    // Crypto
    implementation(identityLibs.nimbus.jose.jwt)
    implementation(identityLibs.bouncycastle.prov)
    implementation(identityLibs.bouncycastle.pkix)
}

application {
    mainClass.set("id.walt.issuer2.MainKt")
}

buildConfig {
    packageName("id.walt.issuer2")
}

ktor {
    docker {
        portMappings.set(
            listOf(
                DockerPortMapping(7004, 7004, DockerPortMappingProtocol.TCP)
            )
        )
    }
}
