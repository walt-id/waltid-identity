import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("jvm")
    kotlin("plugin.power-assert") version "2.0.0"
}

group = "id.walt"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.8.1")
    testImplementation("io.ktor:ktor-server-test-host:2.3.12")
    testImplementation("io.ktor:ktor-client-cio:2.3.12")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    testImplementation("io.ktor:ktor-client-logging:2.3.12")
    testImplementation("com.github.ajalt.mordant:mordant:2.7.1")
    implementation(project(":waltid-services:waltid-service-commons"))
    implementation(project(":waltid-services:waltid-issuer-api"))
    implementation(project(":waltid-services:waltid-verifier-api"))
    implementation(project(":waltid-services:waltid-wallet-api"))

    testImplementation("app.softwork:kotlinx-uuid-core:0.0.26")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    testImplementation("com.augustcellars.cose:cose-java:1.1.0")
    // Multiplatform / Hashes
    testImplementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.4.0"))
    testImplementation("org.kotlincrypto.hash:sha2")

}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        // kotlin.test
        "kotlin.assert", "kotlin.test.assertTrue", "kotlin.test.assertEquals", "kotlin.test.assertNull",

        // checks
        "kotlin.require", "kotlin.check"
    )
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
