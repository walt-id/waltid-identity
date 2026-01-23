@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

object Versions {
    const val KTOR_VERSION = "3.3.3"
    const val COROUTINES_VERSION = "1.10.2"
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
    implementation("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-sessions-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auto-head-response-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-double-receive-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-host-common-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-compression-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-cors-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-call-logging-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-call-id-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-sse-jvm:${Versions.KTOR_VERSION}")

    // Ktor client
    implementation("io.ktor:ktor-client-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-json-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")

    // Uuid
    implementation("app.softwork:kotlinx-uuid-core:0.1.6")

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.5")
    implementation("org.slf4j:jul-to-slf4j:2.0.17")
    implementation("io.klogging:klogging-jvm:0.11.6")
    implementation("io.klogging:slf4j-klogging:0.11.6")
    implementation("io.ktor:ktor-client-encoding:3.2.2")

    // Test

    implementation("com.nimbusds:nimbus-jose-jwt:10.6")

    implementation(identityLibs.kotlintest)
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(project(":waltid-libraries:protocols:waltid-openid4vp-wallet"))
    testImplementation(project(":waltid-libraries:credentials:waltid-holder-policies"))

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
