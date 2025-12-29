plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    
    // Azure Identity (for Managed Identity authentication)
    implementation("com.azure:azure-identity:1.15.1")

    // Azure Key Vault Keys (for cryptographic operations)
    implementation("com.azure:azure-security-keyvault-keys:4.9.1")

    // JOSE
    implementation("com.nimbusds:nimbus-jose-jwt:10.6")

    // Hashing with SHA-2
    implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
    implementation("org.kotlincrypto.hash:sha2")
}

tasks.withType<Test> {
    enabled = false
}

mavenPublishing {
    pom {
        name.set("Walt.id Crypto Azure")
        description.set("Walt.id Crypto Azure Key Vault Integration")
    }
}