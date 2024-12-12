plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization")
}

group = "id.walt.crypto"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    //aws
    implementation("aws.sdk.kotlin:kms:1.3.91")
    implementation("aws.sdk.kotlin:dynamodb:1.3.91")
    implementation("aws.sdk.kotlin:secretsmanager:1.3.91")


    // JOSE
    implementation("com.nimbusds:nimbus-jose-jwt:9.41.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}