plugins {
    id("waltid.ktorbackend")
}

group = "id.walt"
version = "0.0.1"

application {
    mainClass.set("id.walt.ApplicationKt")
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

    // OIDC
    implementation("com.nimbusds:nimbus-jose-jwt:10.6")

    // for Ed25519
    implementation("com.google.crypto.tink:tink:1.16.0") {
        exclude("org.slf4j.simple")
    }

    // Logging
    implementation("io.klogging:klogging-jvm:0.11.6")
    implementation("io.klogging:slf4j-klogging:0.11.6")

    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
