plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

dependencies {
    testImplementation(kotlin("test"))
    implementation(identityLibs.kotlinx.coroutines.test)

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))

    // JSON
    implementation(identityLibs.kotlinx.serialization.json)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    // AWS
    implementation(identityLibs.aws.kms)

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
        name.set("walt.id Crypto AWS")
        description.set("walt.id Crypto AWS Integration")
    }
}
