import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("jvm")
    kotlin("plugin.power-assert")
    id("com.github.ben-manes.versions")
}

group = "id.walt"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.1")
    testImplementation("io.ktor:ktor-server-test-host:3.1.0")
    testImplementation("io.ktor:ktor-client-cio:3.1.0")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.1.0")
    testImplementation("io.ktor:ktor-client-logging:3.1.0")


    // Command line formatting
    testImplementation("com.github.ajalt.mordant:mordant:2.7.1")

    // Libraries to test
    testImplementation(project(":waltid-services:waltid-service-commons-test"))
    testImplementation(project(":waltid-services:waltid-issuer-api"))
    testImplementation(project(":waltid-services:waltid-verifier-api"))
    testImplementation(project(":waltid-services:waltid-wallet-api"))

    testImplementation("app.softwork:kotlinx-uuid-core:0.1.2")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    testImplementation("com.augustcellars.cose:cose-java:1.1.0")
    testImplementation("org.bouncycastle:bcpkix-lts8on:2.73.7")

    // Multiplatform / Hashes
    testImplementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
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
