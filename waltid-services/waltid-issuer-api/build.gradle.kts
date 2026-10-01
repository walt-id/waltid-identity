import io.ktor.plugin.features.*

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

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.serialization)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.json)
    implementation(identityLibs.ktor.client.java)
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
    implementation(identityLibs.hoplite.core)
    implementation(identityLibs.hoplite.hocon)

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)

    // OIDC
    api(project(":waltid-libraries:protocols:waltid-openid4vc"))

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-azure"))

    api(project(":waltid-libraries:waltid-did"))

    api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
    api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
    api(project(":waltid-libraries:credentials:waltid-verification-policies"))
    api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))

    // crypto
    implementation(identityLibs.java.cose)
    implementation(identityLibs.nimbus.jose.jwt)
    // Bouncy Castle
    implementation(identityLibs.bouncycastle.prov)
    implementation(identityLibs.bouncycastle.pkix)

    // Multiplatform / Hashes
    testImplementation(identityLibs.kotlincrypto.hash.sha2)
}

application {
    mainClass.set("id.walt.issuer.MainKt")
}

buildConfig {
    packageName("id.walt.issuer")
}

ktor {
    docker {
        portMappings.set(
            listOf(
                DockerPortMapping(7002, 7002, DockerPortMappingProtocol.TCP)
            )
        )
    }
}
