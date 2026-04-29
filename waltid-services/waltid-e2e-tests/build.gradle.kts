plugins {
    id("waltid.jvm.servicelib")
}

group = "id.walt"

dependencies {
    val ktorVersion = "3.3.3"

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.2")
    testImplementation(identityLibs.ktor.server.test.host)
    testImplementation(identityLibs.ktor.client.cio)
    testImplementation(identityLibs.ktor.client.content.negotiation)
    testImplementation(identityLibs.ktor.client.logging)


    // Command line formatting
    testImplementation("com.github.ajalt.mordant:mordant:3.0.2")

    // Libraries to test
    testImplementation(project(":waltid-services:waltid-service-commons-test"))
    testImplementation(project(":waltid-services:waltid-issuer-api"))
    testImplementation(project(":waltid-services:waltid-verifier-api"))
    testImplementation(project(":waltid-services:waltid-wallet-api"))

    testImplementation("com.nimbusds:nimbus-jose-jwt:10.9")
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
    testImplementation("org.bouncycastle:bcpkix-lts8on:2.73.10")

    // Multiplatform / Hashes
    testImplementation(identityLibs.kotlincrypto.hash.sha2)

}
