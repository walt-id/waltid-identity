plugins {
    id("waltid.jvm.servicelib")
    id("waltid.publish.maven")
    id("waltid.dependency-constraints")
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
    // core, databind and CBOR, CVE-2026-68495). The Jackson 3 BOM stays published from this library. The patched
    // catalog versions, including CBOR, are exported by waltid.dependency-constraints.
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

// web3j:core still requests bcprov-jdk18on 1.80. Keep this local: the 4.2 Netty
// pins live in waltid.dependency-constraints and must not be mixed with Azure's
// Netty 4.1 forces.
configurations.all {
    resolutionStrategy.force(identityLibs.bouncycastle.prov)
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
