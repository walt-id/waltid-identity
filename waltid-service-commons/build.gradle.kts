plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
//    id("io.ktor.plugin") version "2.3.11"
}

group = "id.walt"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

object Versions {
    const val KTOR_VERSION = "2.3.11" // also change 1 plugin
}

dependencies {
    // Ktor
    api("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")

    // Logging
    api("io.klogging:klogging-jvm:0.5.14") // JVM + ~JS
    implementation("io.klogging:slf4j-klogging:0.5.13")
    implementation("org.slf4j:jul-to-slf4j:2.0.13")

    // CLI
    api("com.github.ajalt.clikt:clikt:4.4.0")  // JVM

    // Config
    api("com.sksamuel.hoplite:hoplite-core:2.8.0.RC3")
    api("com.sksamuel.hoplite:hoplite-hocon:2.8.0.RC3")
    api("com.sksamuel.hoplite:hoplite-hikaricp:2.8.0.RC3")

    // Kotlinx.serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Health checks
    api("com.sksamuel.cohort:cohort-ktor:2.5.1")

    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
