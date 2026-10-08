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
    implementation(identityLibs.commons.lang3)

    // TOTP/HOTP
    implementation(identityLibs.onetime)

    // JWT
    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
    implementation(project(":waltid-libraries:crypto:waltid-jose"))
    implementation(identityLibs.nimbus.jose.jwt)

    // Cryptography
    /*implementation(platform("dev.whyoleg.cryptography:cryptography-bom:0.4.0"))
    implementation("dev.whyoleg.cryptography:cryptography-core")
    implementation("dev.whyoleg.cryptography:cryptography-provider-jdk")*/
    implementation(identityLibs.password4j)
    implementation(identityLibs.kotlincrypto.hash.sha2)
    implementation(identityLibs.kotlincrypto.random)

    // Ktor server
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.server.authjwt)
    //implementation("io.ktor:ktor-server-auth-ldap")
    implementation(identityLibs.ktor.server.sessions)
    implementation(identityLibs.ktor.server.auto.head.response)
    implementation(identityLibs.ktor.server.double.receive)
    implementation(identityLibs.ktor.server.host.common)
    implementation(identityLibs.ktor.server.status.pages)
    implementation(identityLibs.ktor.server.cors)
    implementation(identityLibs.ktor.server.default.headers)
    implementation(identityLibs.ktor.server.forwarded.header)
    implementation(identityLibs.ktor.server.call.logging)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.server.cio)
    implementation(identityLibs.ktor.server.html.builder)

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.apache5)
    implementation(identityLibs.ktor.client.content.negotiation)

    // Ktor shared
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    // Ktor server external
    implementation(identityLibs.smiley.ktor.openapi)
    implementation(identityLibs.smiley.ktor.swaggerui)
    implementation(identityLibs.smiley.ktor.redoc)

    // JSON
    implementation(identityLibs.kotlinx.serialization.json)
    implementation(identityLibs.kotlinx.datetime)
    implementation(identityLibs.jsonpathkt)

    // Logging
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    // Redis
    //implementation("eu.vendeli:rethis:0.3.3")
    implementation(identityLibs.kedis)

    /* --- Testing --- */
    testImplementation(identityLibs.ktor.client.logging)
    testImplementation(identityLibs.kotlinx.coroutines.test)

    // Ktor
    testImplementation(identityLibs.ktor.server.cio)
    testImplementation(identityLibs.ktor.server.test.host)

    // Kotlin
    testImplementation(kotlin("test"))
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
// ktor-openapi → io.netty (4.2.x branch), pinned to the netty-4_2 catalog version.
// web3j → tuweni → vertx-core. swagger-parser → json-schema-core → rhino.
configurations.all {
    resolutionStrategy.force(
        identityLibs.jackson.core.tools,
        identityLibs.jackson.databind.tools,
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
