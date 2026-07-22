@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
    id("waltid.mokkery")
}

group = "id.walt.credentials"

kotlin {
    js(IR) {
        outputModuleName.set("digital-credentials")
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.serialization.cbor)

            // Ktor client
            implementation(identityLibs.ktor.client.core)

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // walt.id
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            api(project(":waltid-libraries:crypto:waltid-jose"))
            api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            api(project(":waltid-libraries:credentials:waltid-credential-key-resolver"))
            api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            api(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
            api(project(":waltid-libraries:credentials:waltid-dcql"))
            api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:crypto:waltid-x509"))


            implementation(identityLibs.kotlincrypto.hash.sha2)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)

            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.junit.jupiter.params)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Digital Credentials")
        description.set("walt.id Kotlin/Java library for Digital Credentials")
    }
}
