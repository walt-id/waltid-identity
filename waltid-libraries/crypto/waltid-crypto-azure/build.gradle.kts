plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.junit.jupiter.api)

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))

    // JSON
    implementation(identityLibs.kotlinx.serialization.json)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")

    // Azure Identity (for Managed Identity authentication)
    implementation("com.azure:azure-identity:1.19.0-beta.2")

    // Azure Key Vault Keys (for cryptographic operations)
    implementation("com.azure:azure-security-keyvault-keys:4.10.7")

    // JOSE
    implementation(identityLibs.nimbus.jose.jwt)

    // Hashing with SHA-2

    implementation(identityLibs.kotlincrypto.hash.sha2)
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
