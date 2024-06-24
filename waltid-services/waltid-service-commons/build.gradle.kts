plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "id.walt"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

object Versions {
    const val KTOR_VERSION = "2.3.12" // also change 1 plugin
}

dependencies {
    // Ktor
    api("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")

    // Logging
    api("io.klogging:klogging-jvm:0.5.14") // JVM + ~JS
    implementation("io.klogging:slf4j-klogging:0.5.14")
    implementation("org.slf4j:jul-to-slf4j:2.0.13")

    // CLI
    api("com.github.ajalt.clikt:clikt:4.4.0")  // JVM

    // Config
    api("com.sksamuel.hoplite:hoplite-core:2.8.0.RC3")
    api("com.sksamuel.hoplite:hoplite-hocon:2.8.0.RC3")
    api("com.sksamuel.hoplite:hoplite-hikaricp:2.8.0.RC3")

    // Kotlinx.serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")

    // Health checks
    api("com.sksamuel.cohort:cohort-ktor:2.5.1")

    // OpenAPI
    implementation("io.github.smiley4:ktor-swagger-ui:3.0.1")
    implementation("io.github.smiley4:schema-kenerator-core:1.0.0")
    implementation("io.github.smiley4:schema-kenerator-serialization:1.0.1")
    implementation("io.github.smiley4:schema-kenerator-reflection:1.0.0")
    implementation("io.github.smiley4:schema-kenerator-swagger:1.0.0")

    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
