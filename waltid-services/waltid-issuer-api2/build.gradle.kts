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

    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
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

    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.java)
    implementation(identityLibs.ktor.client.logging)

    implementation(identityLibs.ktor.serialization.kotlinx.json)
    implementation(identityLibs.kotlinx.coroutines.core)
    implementation(identityLibs.kotlinx.datetime)
    implementation(identityLibs.jsonpathkt)
    implementation(identityLibs.jaywayjsonpath)

    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    api(project(":waltid-libraries:protocols:waltid-openid4vci"))
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-azure"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
    api(project(":waltid-libraries:waltid-did"))

    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(identityLibs.bundles.waltid.ktortesting)
    testImplementation(project(":waltid-libraries:protocols:waltid-openid4vci-wallet"))
    testImplementation(identityLibs.junit.jupiter.api)
    testRuntimeOnly(identityLibs.junit.jupiter.engine)
    testRuntimeOnly(identityLibs.junit.platform.launcher)
}

application {
    mainClass.set("id.walt.issuer2.MainKt")
}

buildConfig {
    packageName("id.walt.issuer2")
}

ktor {
    docker {
        portMappings.set(listOf(DockerPortMapping(7002, 7002, DockerPortMappingProtocol.TCP)))
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("redis")
    }
}

tasks.register<Test>("redisTest") {
    description = "Runs issuer2 Redis-backed repository tests. Requires ISSUER2_REDIS_HOST."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("redis")
    }
    shouldRunAfter(tasks.test)
}
