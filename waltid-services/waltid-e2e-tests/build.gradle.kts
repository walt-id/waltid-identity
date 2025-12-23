plugins {
    id("waltid.jvm.servicelib")
}

group = "id.walt"

dependencies {
    val ktorVersion = "3.3.3"

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.2")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-logging:$ktorVersion")


    // Command line formatting
    testImplementation("com.github.ajalt.mordant:mordant:3.0.2")

    // Libraries to test
    testImplementation(project(":waltid-services:waltid-service-commons-test"))
    testImplementation(project(":waltid-services:waltid-issuer-api"))
    testImplementation(project(":waltid-services:waltid-verifier-api"))
    testImplementation(project(":waltid-services:waltid-wallet-api"))

    testImplementation("app.softwork:kotlinx-uuid-core:0.1.6")
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.6")
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
    testImplementation("org.bouncycastle:bcpkix-lts8on:2.73.8")

    // Multiplatform / Hashes
    testImplementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
    testImplementation("org.kotlincrypto.hash:sha2")

}
