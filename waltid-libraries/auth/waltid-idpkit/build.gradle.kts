plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.12"
    kotlin("plugin.serialization")
    id("maven-publish")

    id("com.github.ben-manes.versions")
}

group = "id.walt"
version = "0.0.1"

application {
    mainClass.set("id.walt.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-sessions")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-double-receive")

    // Ktor client
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

    // OIDC
    implementation("com.nimbusds:oauth2-oidc-sdk:11.12")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")

    // Logging
    implementation("io.klogging:klogging-jvm:0.7.0")
    implementation("io.klogging:slf4j-klogging:0.7.0")

    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
