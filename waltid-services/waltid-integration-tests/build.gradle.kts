plugins {
    id("waltid.jvm.servicelib")
}

group = "id.walt"

dependencies {
    val ktorVersion = "3.4.0"

    // Testing
    implementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.2")
    implementation("io.ktor:ktor-server-test-host:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")


    // Command line formatting
    implementation("com.github.ajalt.mordant:mordant:3.0.2")

    // Libraries to test
    implementation(project(":waltid-services:waltid-service-commons-test"))
    implementation(project(":waltid-services:waltid-issuer-api"))
    implementation(project(":waltid-services:waltid-verifier-api"))
    implementation(project(":waltid-services:waltid-wallet-api"))

    implementation("app.softwork:kotlinx-uuid-core:0.1.7")
    implementation("com.nimbusds:nimbus-jose-jwt:10.8")
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
    implementation("org.bouncycastle:bcpkix-lts8on:2.73.10")

    implementation("org.junit.jupiter:junit-jupiter-engine:6.0.3")
    implementation("org.junit.jupiter:junit-jupiter-params:6.0.3")
    implementation("org.junit.platform:junit-platform-launcher:6.0.3")
    implementation("org.junit.platform:junit-platform-console:6.0.3")


    // Multiplatform / Hashes
    implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.8.0"))
    implementation("org.kotlincrypto.hash:sha2")

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
