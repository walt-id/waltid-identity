@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.crypto"

kotlin {

    js {
        outputModuleName = "x509"
        nodejs {
            testTask {
                useMocha()
                enabled = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.io.core)
            implementation(identityLibs.kotlinx.io.bytestring)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.whyoleg.cryptography.random)

        }
        commonTest.dependencies {
            implementation(identityLibs.kotlin.test)
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.kotlinx.serialization.json)
        }

        val commonSignumMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(project(":waltid-libraries:crypto:waltid-crypto"))
                implementation(identityLibs.signum.indispensable)
                implementation(identityLibs.kotlinx.io.bytestring)
            }
        }

        val commonSignumTest by creating {
            dependsOn(commonTest.get())
            dependsOn(commonSignumMain)
        }

        val jvmIosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(project(":waltid-libraries:crypto:waltid-crypto"))
                implementation(identityLibs.signum.indispensable)
                implementation(identityLibs.signum.supreme)
            }
        }

        jvmMain {
            dependsOn(commonSignumMain)
            dependsOn(jvmIosMain)
            dependencies {
                implementation(identityLibs.signum.indispensable)
                implementation(identityLibs.bouncycastle.prov)
                implementation(identityLibs.bouncycastle.pkix)
                implementation(identityLibs.nimbus.jose.jwt)
                implementation(identityLibs.kotlinx.coroutines.core)
            }
        }

        jvmTest {
            dependsOn(jvmMain.get())
            dependsOn(commonSignumTest)
            dependencies {

                // Logging
                implementation(identityLibs.slf4j.simple)

                // Ktor client
                implementation(identityLibs.ktor.client.java)

                // Test
                implementation(kotlin("test"))
                implementation(identityLibs.junit.jupiter.api)
                implementation(identityLibs.junit.jupiter.engine)
                implementation(identityLibs.junit.jupiter.params)

                implementation(identityLibs.bouncycastle.prov)
                implementation(identityLibs.nimbus.jose.jwt)
            }
        }


        jsMain {
            dependsOn(commonSignumMain)
            dependencies {
                implementation(identityLibs.signum.indispensable)
            }
        }


        /*


                jvmMain.get().dependsOn(jvmIosMain)
                if (enableIosBuild) {
                    iosMain.get().dependsOn(jvmIosMain)
                }


                jsTest {
                    dependsOn(commonSignumMain)
                }*/
    }

    if (enableAndroidBuild) {
        // Signum's Android artifacts bring jdk18on Bouncy Castle; this project
        // already uses lts8on via the shared JVM/Android crypto stack.
        configurations.all {
            exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
            exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
            exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id X.509")
        description.set("walt.id Kotlin/Java library X.509")
    }
}
