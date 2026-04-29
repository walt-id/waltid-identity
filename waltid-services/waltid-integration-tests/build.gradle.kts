plugins {
    id("waltid.jvm.servicelib")
}

group = "id.walt"

dependencies {
    // Testing
    implementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.2")
    implementation(identityLibs.ktor.server.test.host)
    implementation(identityLibs.ktor.client.cio)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.logging)


    // Command line formatting
    implementation("com.github.ajalt.mordant:mordant:3.0.2")

    // Libraries to test
    implementation(project(":waltid-services:waltid-service-commons-test"))
    implementation(project(":waltid-services:waltid-issuer-api"))
    implementation(project(":waltid-services:waltid-verifier-api"))
    implementation(project(":waltid-services:waltid-wallet-api"))

    implementation(identityLibs.nimbus.jose.jwt)
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
    implementation(identityLibs.bouncycastle.pkix)

    implementation(identityLibs.junit.jupiter.params)
    implementation(identityLibs.junit.platform.launcher)
    implementation(identityLibs.junit.platform.console)


    // Multiplatform / Hashes

    implementation(identityLibs.kotlincrypto.hash.sha2)

}

sourceSets {
// temporary: remove integration tests failing with unresolved address
//    test {
//        kotlin.setSrcDirs(listOf("src/main/kotlin/", "src/test/kotlin"))
//    }
}


kotlin {
    jvmToolchain(21)
}

/*tasks.test {
    useJUnitPlatform()
}*/
