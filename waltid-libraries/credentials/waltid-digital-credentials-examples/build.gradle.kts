@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

kotlin {
    js(IR) {
        outputModuleName.set("digital-credentials-examples")
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.9.0")

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // walt.id
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            api(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
            api(project(":waltid-libraries:credentials:waltid-dcql"))
            api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            api(project(":waltid-libraries:waltid-did"))

            // Hashing
            implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
            implementation("org.kotlincrypto.hash:sha2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            // Ktor client
            // implementation("io.ktor:ktor-client-okhttp:$ktor_version")
        }
        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Digital Credentials Examples")
        description.set("walt.id Kotlin/Java library for Examples of Digital Credentials")
    }
}
