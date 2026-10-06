plugins {
    id("waltid.jvm.servicelib")
}

group = "id.walt"

dependencies {
    // Testing
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.service.test.jvm)
    testImplementation(identityLibs.ktor.server.test.host)
    testImplementation(identityLibs.ktor.client.java)
    testImplementation(identityLibs.ktor.client.content.negotiation)
    testImplementation(identityLibs.ktor.client.logging)


    // Command line formatting
    testImplementation(identityLibs.mordant)

    // Libraries to test
    testImplementation(project(":waltid-services:waltid-service-commons-test"))
    testImplementation(project(":waltid-services:waltid-issuer-api"))
    testImplementation(project(":waltid-services:waltid-verifier-api"))
    testImplementation(project(":waltid-services:waltid-wallet-api"))

    testImplementation(identityLibs.nimbus.e2e)
    implementation(identityLibs.java.cose)
    testImplementation(identityLibs.bouncycastle.pkix)

    // Multiplatform / Hashes
    testImplementation(identityLibs.kotlincrypto.hash.sha2)

}
