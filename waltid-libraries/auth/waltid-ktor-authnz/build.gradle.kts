plugins {
    id("waltid.jvm.servicelib")
    id("waltid.publish.maven")
}

group = "id.walt"

dependencies {
    // Authentication exceptions carry their HTTP status
    api(project(":waltid-libraries:waltid-library-commons"))

    // Auth methods
    // Core Web3j library
    implementation(identityLibs.web3j.core)

    // Optional: Web3j utils
    implementation(identityLibs.web3j.utils)


    // RADIUS
    implementation(identityLibs.aaa4j.radius)

    // LDAP
    implementation(identityLibs.apache.ldap.api) {
        exclude("org.apache.mina:mina-core") // Manually updated due to security CVE
        exclude("org.apache.commons:commons-lang3") // Manually updated due to security CVE
    }
    implementation(identityLibs.mina.core)
    // Not used directly: pins the version ktor-openapi brings (CVE-2025-48924) for users of this library too.
    implementation(identityLibs.commons.lang3)

    // TOTP/HOTP
    implementation(identityLibs.onetime)

    // JWT
    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
    implementation(project(":waltid-libraries:crypto:waltid-jose"))
    implementation(identityLibs.nimbus.jose.jwt)

    // Cryptography
    implementation(identityLibs.password4j)
    implementation(identityLibs.kotlincrypto.hash.sha2)
    implementation(identityLibs.kotlincrypto.random)

    // Ktor server
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.server.status.pages)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.server.html.builder)

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    // The engine of the OIDC and VC clients (`HttpClient {}` picks it from the classpath)
    implementation(identityLibs.ktor.client.apache5)
    implementation(identityLibs.ktor.client.content.negotiation)

    // Ktor shared
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    // Ktor server external
    implementation(identityLibs.smiley.ktor.openapi)

    // JSON
    implementation(identityLibs.kotlinx.serialization.json)

    // Logging
    implementation(identityLibs.klogging)

    // Redis
    implementation(identityLibs.kedis)

    // Passkeys (WebAuthn). It parses the CBOR browsers send with Jackson 3, whose BOM it lifts to 3.2.1 (vulnerable
    // core, databind and CBOR). The forced versions below only hold in this build, so the BOM is also published.
    implementation(identityLibs.webauthn4j.core)
    implementation(platform(identityLibs.jackson.bom.tools))

    /* --- Testing --- */
    testImplementation(identityLibs.ktor.client.logging)
    testImplementation(identityLibs.kotlinx.coroutines.test)

    // Ktor
    testImplementation(identityLibs.ktor.server.cio)
    // The example web app of the tests
    testImplementation(identityLibs.smiley.ktor.swaggerui)
    testImplementation(identityLibs.smiley.ktor.redoc)
    testImplementation(identityLibs.ktor.server.auto.head.response)
    testImplementation(identityLibs.ktor.server.double.receive)
    testImplementation(identityLibs.unboundid.ldapsdk)
    testImplementation(identityLibs.ktor.server.test.host)

    // Kotlin
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.webauthn4j.test)
}

// Force-pin vulnerable transitive dependencies.
//
// web3j:core → tools.jackson.core:jackson-core / jackson-databind
//   Pin both to the jackson-core-3 catalog version.
//
// web3j:core / ktor-openapi → com.fasterxml.jackson.core:jackson-core / jackson-databind
//   Pin both to the jackson-core catalog version.
//
// web3j:core → org.bouncycastle:bcprov-jdk18on:1.80
//   CWE-327 (broken crypto, CVSS 8.7), CWE-1240 (timing attack), CWE-90 (LDAP injection)
//   Snyk said "no supported fix" at 1.80; 1.84 is now available
//
// webauthn4j → tools.jackson.dataformat:jackson-dataformat-cbor:3.2.1, allocation without limits
//   (CVE-2026-68495, fixed in 3.2.2), and through its BOM core and databind 3.2.1. Pinned to the jackson-core-3
//   catalog version as well; users of this library get it through the published BOM (see the dependencies).
//
// ktor-openapi → io.netty (4.2.x branch), pinned to the netty-4_2 catalog version.
// web3j → tuweni → vertx-core. swagger-parser → json-schema-core → rhino.
configurations.all {
    resolutionStrategy.force(
        identityLibs.jackson.core.tools,
        identityLibs.jackson.databind.tools,
        identityLibs.jackson.dataformat.cbor.tools,
        identityLibs.jackson.core,
        identityLibs.jackson.databind,
        identityLibs.bouncycastle.prov,
        identityLibs.netty.codec.compression,
        identityLibs.netty.codec.http.v2,
        identityLibs.netty.transport.classes.epoll,
        identityLibs.vertx.core,
        identityLibs.rhino,
    )
}

mavenPublishing {
    pom {
        name.set("walt.id ktor-authnz")
        description.set(
            """
            Kotlin/Java library for AuthNZ
            """.trimIndent()
        )
    }
}
