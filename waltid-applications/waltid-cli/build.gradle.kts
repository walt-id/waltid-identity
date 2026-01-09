@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

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

            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("com.google.code.gson:gson:2.12.1")

            // CLI
            implementation("com.github.ajalt.clikt:clikt:5.0.3")
            implementation("com.github.ajalt.clikt:clikt-markdown:5.0.3")
            implementation("com.github.ajalt.mordant:mordant:3.0.2")
            implementation("com.github.ajalt.mordant:mordant-markdown:3.0.2")

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

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
            implementation("com.nimbusds:nimbus-jose-jwt:10.6")

            // BouncyCastle for PEM import
            implementation("org.bouncycastle:bcpkix-lts8on:2.73.8")
        }
        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("com.wolpl.clikt-testkit:clikt-testkit:3.0.0")

            implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")

            // Ktor server
            implementation("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-netty-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-sessions-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-auth-jwt-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-auto-head-response-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-double-receive-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-host-common-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-compression-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-cors-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-forwarded-header-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-call-logging-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-call-id-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")

            // Ktor client
            implementation("io.ktor:ktor-client-core-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-serialization-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-json-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")

        }

    }
}
