@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.crypto"


kotlin {
    js(IR) {
        outputModuleName = "cose"
    }

    sourceSets {
        commonMain.dependencies {
            // Logging
            implementation("io.github.oshai:kotlin-logging:7.0.13")

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            // CBOR
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.9.0")

            // Waltid
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))

            // Hashing
            implementation(project.dependencies.platform("org.kotlincrypto.macs:bom:0.7.1"))
            implementation("org.kotlincrypto.macs:hmac-sha2")

            implementation(identityLibs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
        jvmMain.dependencies {

        }
        jvmTest.dependencies {
            // Logging
            implementation("org.slf4j:slf4j-simple:2.0.17")

            // Test
            implementation(kotlin("test"))

            implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
            implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
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
        name.set("walt.id Holder Policies")
        description.set("walt.id Kotlin/Java library for Holder Policies")
    }
}
