import io.ktor.plugin.features.*

plugins {
    id("waltid.ktorbackend")
    id("waltid.ktordocker")
}

group = "id.walt"

application {
    mainClass.set("id.walt.webwallet.MainKt")
}


dependencies {
    implementation(project(":waltid-services:waltid-service-commons"))

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
    implementation(identityLibs.ktor.serialization.kotlinx.json)
    implementation(identityLibs.ktor.server.cio)
    implementation(identityLibs.ktor.server.method.override)
    implementation(identityLibs.ktor.server.rate.limit)

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.serialization)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.json)
    implementation(identityLibs.ktor.client.java)
    implementation(identityLibs.ktor.client.logging)

    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation(identityLibs.kotlinx.serialization.json)

    // Date
    implementation(identityLibs.kotlinx.datetime)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    /* -- Security -- */
    // Bouncy Castle
    implementation(identityLibs.bouncycastle.prov)
    implementation(identityLibs.bouncycastle.pkix)

    // Argon2i password hashes. Apache-2.0; verifies hashes previously produced by argon2-jvm.
    implementation(identityLibs.password4j)


    // walt.id
    implementation(project(":waltid-libraries:protocols:waltid-openid4vc"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp-clientidprefix"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp-wallet"))
    implementation(project(":waltid-libraries:credentials:waltid-dcql"))
    implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
    implementation(project(":waltid-libraries:credentials:waltid-holder-policies"))
    implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))

    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-oci"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-azure"))



    implementation(project(":waltid-libraries:waltid-did"))
    implementation(project(":waltid-libraries:credentials:waltid-verification-policies"))
    implementation(project(":waltid-libraries:credentials:waltid-dif-definitions-parser"))

    implementation(project(":waltid-libraries:auth:waltid-ktor-authnz"))

    testImplementation(project(":waltid-services:waltid-issuer-api"))
    testImplementation(project(":waltid-services:waltid-verifier-api"))

    implementation(identityLibs.nimbus.jose.jwt)
    implementation(identityLibs.java.cose)

    implementation(identityLibs.ktor.client.java)

    /* -- Misc --*/

    // Cache
    implementation(identityLibs.cache4k)

    // Webauthn
    /*implementation("com.webauthn4j:webauthn4j-core:0.28.5.RELEASE") {
        exclude("ch.qos.logback")
    }*/ // Not implemented right now

    // DB
    implementation(identityLibs.exposed.wallet.core)
    implementation(identityLibs.exposed.wallet.jdbc)
    implementation(identityLibs.exposed.wallet.dao)
    implementation(identityLibs.exposed.wallet.java.time)
    implementation(identityLibs.exposed.wallet.json)
    // drivers
    implementation(identityLibs.sqlite.wallet)
    implementation(identityLibs.postgresql)
    implementation(identityLibs.mssql.jdbc)

    // Web push
    // implementation("dev.blanke.webpush:webpush:6.1.1") // alternative
    implementation(identityLibs.webpush)

    // Config
    implementation(identityLibs.hoplite.core)
    implementation(identityLibs.hoplite.hocon)
    implementation(identityLibs.hoplite.yaml)
    implementation(identityLibs.hoplite.hikaricp)
    implementation(identityLibs.hikaricp.wallet)

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    // Test
    testImplementation(identityLibs.junit.jupiter.api)
    testImplementation(identityLibs.junit.jupiter.params)
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.service.test)
    testImplementation(identityLibs.ktor.server.test.host)
    testImplementation(identityLibs.mockk.wallet)
    testImplementation(identityLibs.klogging)
}

buildConfig {
    packageName("id.walt.webwallet")
}

ktor {
    docker {
        portMappings.set(
            listOf(
                DockerPortMapping(7001, 7001, DockerPortMappingProtocol.TCP)
            )
        )
    }
}
