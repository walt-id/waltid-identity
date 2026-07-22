@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto2"

kotlin {
    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }
}

dependencies {
    api(project(":waltid-libraries:crypto:waltid-crypto2"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto2-kms"))
    implementation(identityLibs.aws.kms)
    implementation(identityLibs.kotlinx.coroutines.core)
    implementation(identityLibs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(project(":waltid-libraries:crypto:waltid-jose"))
    testImplementation(project(":waltid-libraries:crypto:waltid-cose"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2 AWS SDK provider")
        description.set("Optional JVM AWS SDK KMS provider for walt.id crypto2")
    }
}
