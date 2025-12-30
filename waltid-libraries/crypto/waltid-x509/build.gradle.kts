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
        outputModuleName = "x509"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.kotlinx.coroutines.core)

            // CBOR
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

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

            // Bouncy Castle
            implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
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
        name.set("walt.id X.509")
        description.set("walt.id Kotlin/Java library X.509")
    }
}


