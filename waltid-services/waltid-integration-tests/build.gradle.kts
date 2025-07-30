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
    maven("https://maven.waltid.dev/snapshots")
}

dependencies {
    val ktorVersion = "3.2.0"

    // Testing
    implementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.1")
    implementation("io.ktor:ktor-server-test-host:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")


    // Command line formatting
    implementation("com.github.ajalt.mordant:mordant:3.0.2")

    // Libraries to test
    implementation(project(":waltid-services:waltid-service-commons-test"))
    implementation(project(":waltid-services:waltid-issuer-api"))
    implementation(project(":waltid-services:waltid-verifier-api"))
    implementation(project(":waltid-services:waltid-wallet-api"))

    implementation("app.softwork:kotlinx-uuid-core:0.1.4")
    implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
    implementation("org.bouncycastle:bcpkix-lts8on:2.73.7")

    // Multiplatform / Hashes
    implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
    implementation("org.kotlincrypto.hash:sha2")

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

