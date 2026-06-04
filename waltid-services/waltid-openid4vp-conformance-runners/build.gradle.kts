@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

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
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)
    implementation("io.ktor:ktor-client-encoding:3.5.0")

    // Test

    implementation(identityLibs.nimbus.jose.jwt)

    implementation(identityLibs.kotlintest)
    testImplementation(identityLibs.kotlinx.coroutines.test)
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp-wallet"))
    implementation(project(":waltid-libraries:credentials:waltid-holder-policies"))

    implementation(project(":waltid-services:waltid-service-commons-test"))
    implementation(project(":waltid-services:waltid-verifier-api2"))

    api(project(":waltid-libraries:credentials:waltid-dcql"))
    api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
    api(project(":waltid-libraries:credentials:waltid-verification-policies2"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp-verifier"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp-verifier-openapi"))
    implementation(project(":waltid-libraries:web:waltid-ktor-notifications"))
}

application {
    mainClass.set("id.walt.openid4vp.conformance.MainKt")
}

ktor {
    docker {
        portMappings.set(
            listOf(
                DockerPortMapping(7003, 7003, DockerPortMappingProtocol.TCP)
            )
        )
    }
}
