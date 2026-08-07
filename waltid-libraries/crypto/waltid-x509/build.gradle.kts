@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.full.library")
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
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.io.core)
            implementation(identityLibs.kotlinx.io.bytestring)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.whyoleg.cryptography.random)

            implementation(identityLibs.signum.indispensable) //TODO: get rid of it here
            implementation(identityLibs.signum.indispensable.josef) //TODO: get rid of it here
        }
        commonTest.dependencies {
            implementation(identityLibs.kotlin.test)
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.kotlinx.serialization.json)
        }

        val jvmBouncyMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
                implementation(project(":waltid-libraries:crypto:waltid-crypto"))
                compileOnly(identityLibs.bouncycastle.prov)
                compileOnly(identityLibs.bouncycastle.pkix)
            }
        }

        val jvmBouncyTest by creating {
            dependsOn(commonTest.get())
        }


        val signumMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
                implementation(project(":waltid-libraries:crypto:waltid-crypto"))
                implementation(identityLibs.signum.indispensable)
            }
        }

        val jvmCommon by creating {
            dependsOn(commonMain.get())
        }

        val jvmIosMain by creating {
            dependsOn(signumMain)
            dependencies {
                implementation(identityLibs.signum.supreme)
            }
        }

        jvmMain {
            dependsOn(jvmCommon)
            dependsOn(jvmBouncyMain)
            dependsOn(jvmIosMain)
            dependencies {
                implementation(identityLibs.nimbus.jose.jwt)
                implementation(identityLibs.kotlinx.coroutines.core)
            }
        }

        jvmTest {
            dependsOn(jvmMain.get())
            dependsOn(signumMain)
            dependsOn(jvmBouncyTest)
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
            }
        }

        jsMain {
            dependsOn(signumMain)
        }

        jsTest {
        }

        if (enableAndroidBuild) {
            named("androidMain") {
                dependsOn(jvmCommon)
                dependsOn(jvmBouncyMain)
                dependencies {
                    implementation(identityLibs.kotlinx.coroutines.android)
                    implementation(identityLibs.cryptography.provider.jdk)
                }
            }
        }

        if (enableIosBuild) {
            iosMain {
                dependsOn(jvmIosMain)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id X.509")
        description.set("walt.id Kotlin/Java library X.509")
    }
}
