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
    implementation(identityLibs.kotlinx.serialization.json)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    // AWS
    implementation("aws.sdk.kotlin:kms-jvm:1.6.70")

    // JOSE
    implementation(identityLibs.nimbus.jose.jwt)

    // Hashing with SHA-2

    implementation(identityLibs.kotlincrypto.hash.sha2)
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
