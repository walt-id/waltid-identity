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
    api(project(":waltid-libraries:crypto:waltid-crypto2-kms"))
    implementation(identityLibs.azure.identity)
    implementation(identityLibs.kotlinx.coroutines.reactor)

    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
}

configurations.all {
    resolutionStrategy.force(
        identityLibs.netty.codec,
        identityLibs.netty.codec.dns,
        identityLibs.netty.codec.http,
        identityLibs.netty.codec.http2,
        identityLibs.jackson.core,
    )
}

tasks.withType<Test> {
    useJUnitPlatform()
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2 Azure Identity bridge")
        description.set("Optional Azure Identity token provider for walt.id crypto2 Key Vault REST support")
    }
}
