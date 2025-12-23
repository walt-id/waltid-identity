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

    // AWS
    implementation("aws.sdk.kotlin:kms-jvm:1.5.103")

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
        name.set("Walt.id Crypto AWS")
        description.set("Walt.id Crypto AWS Integration")
    }
}
