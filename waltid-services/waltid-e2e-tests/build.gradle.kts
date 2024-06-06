plugins {
    kotlin("jvm")
}

group = "id.walt"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.8.1")
    testImplementation("io.ktor:ktor-server-test-host:2.3.11")
    testImplementation("io.ktor:ktor-client-cio:2.3.11")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    testImplementation("io.ktor:ktor-client-logging:2.3.11")
    testImplementation("com.github.ajalt.mordant:mordant:2.6.0")
    implementation(project(":waltid-services:waltid-service-commons"))
    implementation(project(":waltid-services:waltid-issuer-api"))
    implementation(project(":waltid-services:waltid-verifier-api"))
    implementation(project(":waltid-services:waltid-wallet-api"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
