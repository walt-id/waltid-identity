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

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
}

tasks.withType<Test> {
    // AWS integration tests require credentials; enable with RUN_AWS_TESTS=true
    // Unit tests for config/model classes run regardless
    enabled = System.getenv("RUN_AWS_TESTS")?.toBoolean() ?: false
    useJUnitPlatform()
}

mavenPublishing {
    pom {
        name.set("walt.id Crypto AWS")
        description.set("walt.id Crypto AWS Integration")
    }
}
