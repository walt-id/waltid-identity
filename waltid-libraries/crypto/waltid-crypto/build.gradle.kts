fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

kotlin {
    js(IR) {
        outputModuleName = "crypto"
    }

    sourceSets {
        commonMain.dependencies {
            // Kotlinx.serialization
            implementation(identityLibs.kotlinx.serialization.json)

            // Kotlinx
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.kotlinx.coroutines.core)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Cache
            implementation(identityLibs.cache4k)

            //

            // Hashes
            implementation(project.dependencies.platform(identityLibs.kotlincrypto.hash.bom))
            implementation(identityLibs.kotlincrypto.hash.sha2)
            implementation(project.dependencies.platform(identityLibs.kotlincrypto.macs.bom))
            implementation(identityLibs.kotlincrypto.macs.hmac.sha2)
        }
        commonTest.dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            // Crypto
            implementation(identityLibs.tink) // for JOSE using Ed25519

            implementation(identityLibs.bouncycastle.prov) // for secp256k1 (which was removed with Java 17)
            implementation(identityLibs.bouncycastle.pkix) // PEM import

            implementation(identityLibs.nimbus.jose.jwt)

            // Ktor client
            implementation(identityLibs.ktor.client.okhttp)

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.jdk8)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))

            // Logging
            implementation(identityLibs.slf4j.simple)

            // Test
            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.junit.jupiter.params)
        }
        jsMain.dependencies {
            // JOSE
            implementation(npm("jose", identityLibs.versions.jose.npm.get()))
        }
        jsTest.dependencies {
            implementation(kotlin("test-js"))
        }
        if (enableIosBuild) {
            val iosMain by creating {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(project(":waltid-libraries:crypto:waltid-target-ios"))
                }
            }

            val iosTest by creating {
                dependsOn(commonTest.get())
            }

            val iosArm64Main by getting { dependsOn(iosMain) }
            val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

            val iosArm64Test by getting { dependsOn(iosTest) }
            val iosSimulatorArm64Test by getting { dependsOn(iosTest) }
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto")
        description.set("walt.id crypto library")
    }
}
