plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin") version "2.3.12"

    application

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
    // Auth methods

    // RADIUS
    implementation("org.aaa4j.radius:aaa4j-radius-client:0.3.0")

    // LDAP
    //implementation("org.apache.directory.server:apacheds-server-integ:2.0.0.AM27")
    implementation("org.apache.directory.api:api-all:2.1.6")

    // TOTP/HOTP
    implementation("com.atlassian:onetime:2.1.1")

    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    //implementation("io.ktor:ktor-server-auth-ldap-jvm")
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-apache-jvm")
    implementation("io.ktor:ktor-server-sessions-jvm")
    implementation("io.ktor:ktor-server-auto-head-response-jvm")
    implementation("io.ktor:ktor-server-double-receive-jvm")
    implementation("io.ktor:ktor-server-webjars-jvm")
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-default-headers-jvm")
    implementation("io.ktor:ktor-server-forwarded-header-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cio-jvm")

    // Ktor server external
    implementation("io.github.smiley4:ktor-swagger-ui:3.2.0")

    // Logging
    implementation("io.klogging:klogging-jvm:0.7.0")
    implementation("io.klogging:slf4j-klogging:0.7.0")

    /* --- Testing --- */

    // Ktor
    testImplementation("io.ktor:ktor-server-cio-jvm")
    testImplementation("io.ktor:ktor-server-tests-jvm")

    // Kotlin
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
