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
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.cio)
    implementation(identityLibs.ktor.server.sessions)
    implementation(identityLibs.ktor.server.status.pages)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.serialization.kotlinx.json)
    implementation(identityLibs.ktor.server.call.logging)
    implementation(identityLibs.ktor.server.double.receive)

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    implementation("io.ktor:ktor-client-cio")
    implementation(identityLibs.ktor.client.content.negotiation)

    // JSON
    implementation(identityLibs.kotlinx.serialization.json)
    implementation(identityLibs.kotlinx.datetime)
    implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

    // OIDC
    implementation(identityLibs.nimbus.jose.jwt)

    // for Ed25519
    implementation(identityLibs.tink) {
        exclude("org.slf4j.simple")
    }

    // Logging
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
