plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.junit.jupiter.api)

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))

    // JSON
    implementation(identityLibs.kotlinx.serialization.json)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")

    // Azure Identity (for Managed Identity authentication)
    // 1.19.0-beta.2 pulled Netty 4.1.130 (multiple CVEs); 1.18.3 pulls 4.1.132 (partially fixed).
    // Remaining CVEs (netty-codec/dns/http/http2 ≥4.1.133, jackson-core ≥2.18.6) are force-pinned below.
    implementation("com.azure:azure-identity:1.18.3")

    // Azure Key Vault Keys (for cryptographic operations)
    implementation("com.azure:azure-security-keyvault-keys:4.10.5")

    // JOSE
    implementation(identityLibs.nimbus.jose.jwt)

    // Hashing with SHA-2

    implementation(identityLibs.kotlincrypto.hash.sha2)
}

// Force-pin vulnerable transitive dependencies brought in by azure-identity → azure-core-http-netty.
// netty ≥4.1.133.Final fixes: CVE-2026-42583, CVE-2026-42587, CVE-2026-42579, CVE-2026-41417,
//   CVE-2026-42585, CVE-2026-42584 (netty-codec, netty-codec-dns, netty-codec-http, netty-codec-http2)
// jackson-core ≥2.18.6 fixes: SNYK-JAVA-COMFASTERXMLJACKSONCORE-15365924
configurations.all {
    resolutionStrategy.force(
        "io.netty:netty-codec:4.1.133.Final",
        "io.netty:netty-codec-dns:4.1.133.Final",
        "io.netty:netty-codec-http:4.1.133.Final",
        "io.netty:netty-codec-http2:4.1.133.Final",
        "com.fasterxml.jackson.core:jackson-core:2.18.6"
    )
}

tasks.withType<Test> {
    enabled = false
}


tasks.test {
    useJUnitPlatform()
}
mavenPublishing {
    pom {
        name.set("Walt.id Crypto Azure")
        description.set("Walt.id Crypto Azure Key Vault Integration")
    }
}
