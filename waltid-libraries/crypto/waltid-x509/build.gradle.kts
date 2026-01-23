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
        nodejs {
            testTask {
                useMocha()
                enabled = false
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.okio)
            implementation(identityLibs.kotlinx.serialization.json)

        }
        commonTest.dependencies {
            implementation(identityLibs.kotlin.test)
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(identityLibs.bcprov.lts8on)
            implementation(identityLibs.bcpkix.lts8on)
            implementation(identityLibs.nimbus.jose.jwt)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            // Logging
            implementation(identityLibs.slf4j.simple)

            // Test
            implementation(kotlin("test"))
            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.junit.jupiter.engine)

            implementation(identityLibs.bcprov.lts8on)
            implementation(identityLibs.nimbus.jose.jwt)
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


