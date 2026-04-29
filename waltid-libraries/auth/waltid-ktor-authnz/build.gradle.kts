plugins {
    id("waltid.jvm.servicelib")
    id("waltid.publish.maven")
}

group = "id.walt"

dependencies {
    // Auth methods
    // Core Web3j library
    implementation("org.web3j:core:5.0.2")

    // Optional: Web3j utils
    implementation("org.web3j:utils:5.0.2")


    // RADIUS
    implementation("org.aaa4j.radius:aaa4j-radius-client:0.4.0")

    // LDAP
    implementation("org.apache.directory.api:apache-ldap-api:2.1.7") {
        exclude("org.apache.mina:mina-core") // Manually updated due to security CVE
        exclude("org.apache.commons:commons-lang3") // Manually updated due to security CVE
    }
    implementation("org.apache.mina:mina-core:2.2.5")
    implementation("org.apache.commons:commons-lang3:3.20.0")

    // TOTP/HOTP
    implementation("com.atlassian:onetime:2.2.0")

    // JWT
    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-services:waltid-service-commons"))
    implementation(identityLibs.nimbus.jose.jwt)

    // Cryptography
    /*implementation(platform("dev.whyoleg.cryptography:cryptography-bom:0.4.0"))
    implementation("dev.whyoleg.cryptography:cryptography-core")
    implementation("dev.whyoleg.cryptography:cryptography-provider-jdk")*/
    implementation("com.password4j:password4j:1.8.4")
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
    implementation(identityLibs.ktor.client.apache)
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
    implementation("io.github.domgew:kedis:0.0.12")

    /* --- Testing --- */
    testImplementation(identityLibs.ktor.client.logging)
    testImplementation(identityLibs.kotlinx.coroutines.test)

    // Ktor
    testImplementation(identityLibs.ktor.server.cio)
    testImplementation(identityLibs.ktor.server.test.host)

    // Kotlin
    testImplementation(kotlin("test"))
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
