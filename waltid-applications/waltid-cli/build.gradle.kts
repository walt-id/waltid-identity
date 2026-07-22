@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.jvm.application.tasks.CreateStartScripts

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
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            api(project(":waltid-libraries:crypto:waltid-jose"))
            api(project(":waltid-libraries:waltid-did"))
            api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            api(project(":waltid-libraries:credentials:waltid-verification-policies2"))
            api(project(":waltid-libraries:credentials:waltid-verification-policies2-vp"))
            api(project(":waltid-libraries:credentials:waltid-dcql"))
            implementation(project(":waltid-libraries:protocols:waltid-openid4vp"))

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
            implementation(identityLibs.slf4j.simple)

            // Bouncy Castle provides strict DER inspection and JVM secp256k1.
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

tasks.withType<CreateStartScripts>().configureEach {
    doLast {
        windowsScript.writeText(
            """@echo off
setlocal
set "APP_HOME=%~dp0.."
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)
"%JAVA_EXE%" %JAVA_OPTS% %WALTID_OPTS% -classpath "%APP_HOME%\lib\*" id.walt.cli.MainKt %*
exit /b %ERRORLEVEL%
"""
        )
    }
}

tasks.named("jvmTest") {
    dependsOn("installJvmDist")
}
