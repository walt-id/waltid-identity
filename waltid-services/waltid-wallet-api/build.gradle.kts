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

    // Argon2
    implementation("de.mkammerer:argon2-jvm:2.12")


    // walt.id
    implementation(project(":waltid-libraries:protocols:waltid-openid4vc"))
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
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")

    implementation(identityLibs.ktor.client.java)

    /* -- Misc --*/

    // Cache
    implementation("io.github.reactivecircus.cache4k:cache4k:0.14.0")

    // Webauthn
    /*implementation("com.webauthn4j:webauthn4j-core:0.28.5.RELEASE") {
        exclude("ch.qos.logback")
    }*/ // Not implemented right now

    // DB
    val exposedVersion = "1.2.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    // drivers
    implementation("org.xerial:sqlite-jdbc:3.53.0.0")
    implementation("org.postgresql:postgresql:42.7.11")
    implementation("com.mysql:mysql-connector-j:9.7.0")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11")

    // Web push
    // implementation("dev.blanke.webpush:webpush:6.1.1") // alternative
    implementation("com.interaso:webpush:1.3.0")

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-hikaricp:2.9.0")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    // Test
    testImplementation(identityLibs.junit.jupiter.api)
    testImplementation(identityLibs.junit.jupiter.params)
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(identityLibs.ktor.server.test.host)
    testImplementation("io.mockk:mockk:1.14.9")
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
