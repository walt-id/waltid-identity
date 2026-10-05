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

    // TOTP/HOTP
    implementation(identityLibs.onetime)

    // JWT
    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
    implementation(project(":waltid-libraries:crypto:waltid-jose"))
    implementation(identityLibs.nimbus.jose.jwt)

    // Cryptography
    implementation("com.password4j:password4j:1.8.4")
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

    // Passkeys (WebAuthn)
    implementation(identityLibs.webauthn4j.core)

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
// web3j:core → tools.jackson.core:jackson-core:3.1.0
//   SNYK-JAVA-TOOLSJACKSONCORE-15907550 (CWE-770, CVSS 8.7) — fixed in 3.1.1
//
// web3j:core / ktor-openapi → com.fasterxml.jackson.core:jackson-core
//   SNYK-JAVA-COMFASTERXMLJACKSONCORE-15365924 (CWE-770) — fixed in 2.18.6; pin to latest
//
// web3j:core → org.bouncycastle:bcprov-jdk18on:1.80
//   CWE-327 (broken crypto, CVSS 8.7), CWE-1240 (timing attack), CWE-90 (LDAP injection)
//   Snyk said "no supported fix" at 1.80; 1.84 is now available
//
// ktor-openapi → io.netty (4.2.x branch):
//   CVE-2026-42583 (CWE-770, CVSS 8.7) — netty-codec-compression, fixed in 4.2.13.Final
//   CVE-2026-42587 (CWE-409, CVSS 8.7) — netty-codec-compression, fixed in 4.2.13.Final
//   CVE-2026-42577 (CWE-772, CVSS 8.7) — netty-transport-classes-epoll, fixed in 4.2.13.Final
//   HTTP request smuggling (CWE-444, CVSS 8.8) — netty-codec-http, fixed in 4.2.13.Final
configurations.all {
    resolutionStrategy.force(
        identityLibs.jackson.core.tools,
        identityLibs.jackson.core,
        identityLibs.bouncycastle.prov,
        identityLibs.netty.codec.compression,
        identityLibs.netty.codec.http.v2,
        identityLibs.netty.transport.classes.epoll,
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
