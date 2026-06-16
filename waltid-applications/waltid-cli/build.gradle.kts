@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.multiplatform.library.jvm")
}

group = "id.walt.cli"

kotlin {
    jvm {
        binaries {
            executable {
                // Define the main class for the application.
                // Works with:
                //     ../../gradlew run --args="--help"
                mainClass = "id.walt.cli.MainKt"
                applicationName = "waltid"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
            api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            api(project(":waltid-libraries:credentials:waltid-verification-policies"))
            api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            api(project(":waltid-libraries:protocols:waltid-openid4vc"))

            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)

            // CLI
            implementation(identityLibs.clikt.core)
            implementation(identityLibs.clikt.markdown)
            implementation("com.github.ajalt.mordant:mordant:3.0.2")
            implementation("com.github.ajalt.mordant:mordant-markdown:3.0.2")

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            // Logging
            implementation("org.slf4j:slf4j-simple:2.0.17")

            // JOSE
            implementation(identityLibs.nimbus.jose.jwt)

            // BouncyCastle for PEM import
            implementation(identityLibs.bouncycastle.pkix)
        }
        jvmTest.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation("com.wolpl.clikt-testkit:clikt-testkit:3.1.1")

            implementation(identityLibs.junit.jupiter.params)

            // Ktor server

            implementation(identityLibs.ktor.server.core)
            implementation(identityLibs.ktor.server.netty)
            implementation(identityLibs.ktor.server.auth)
            implementation(identityLibs.ktor.server.sessions)
            implementation(identityLibs.ktor.server.authjwt)
            implementation(identityLibs.ktor.server.auto.head.response)
            implementation(identityLibs.ktor.server.double.receive)
            implementation(identityLibs.ktor.server.host.common)
            implementation(identityLibs.ktor.server.status.pages)
            implementation(identityLibs.ktor.server.compression)
            implementation(identityLibs.ktor.server.cors)
            implementation(identityLibs.ktor.server.forwarded.header)
            implementation(identityLibs.ktor.server.call.logging)
            implementation(identityLibs.ktor.server.call.id)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.server.cio)

            // Ktor client
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.ktor.client.serialization)
            implementation(identityLibs.ktor.client.content.negotiation)
            implementation(identityLibs.ktor.client.json)
            implementation(identityLibs.ktor.client.cio)
            implementation(identityLibs.ktor.client.logging)

        }

    }
}
