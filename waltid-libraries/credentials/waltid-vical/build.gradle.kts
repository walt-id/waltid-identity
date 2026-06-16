@file:OptIn(ExperimentalKotlinGradlePluginApi::class)
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

kotlin {
    js(IR) {
        outputModuleName = "vical"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.serialization.cbor)
            implementation(identityLibs.kotlinx.coroutines.core)

            implementation(identityLibs.kotlinx.datetime)

            // walt.id
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:crypto:waltid-cose"))
        }
        commonTest.dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation(identityLibs.kotlinx.coroutines.test)

            implementation(identityLibs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {

        }
        jvmTest.dependencies {
            // Logging
            implementation(identityLibs.slf4j.simple)

            // Test
            implementation(kotlin("test"))

            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.junit.jupiter.params)
        }
        jsMain.dependencies {

        }
        jsTest.dependencies {
            implementation(kotlin("test-js"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id VICAL")
        description.set("walt.id Kotlin/Java library for VICAL")
    }
}
