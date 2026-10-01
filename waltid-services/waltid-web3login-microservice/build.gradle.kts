plugins {
    alias(identityLibs.plugins.kotlin.web3login.jvm)
    alias(identityLibs.plugins.ktor.web3login)
    alias(identityLibs.plugins.kotlin.web3login.serialization)
    id("io.github.ben-manes.versions")
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
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cio-jvm")
    testImplementation(kotlin("test-junit"))

    // nftkit
    implementation(identityLibs.nftkit.web3login) {
        exclude("com.sksamuel.hoplite", "hoplite-core")
        exclude("com.sksamuel.hoplite", "hoplite-yaml")
        exclude("com.sksamuel.hoplite", "hoplite-hikaricp")
    }
}
