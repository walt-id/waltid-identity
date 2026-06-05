fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

kotlin {
    js(IR) {
        outputModuleName = "crypto"
    }

    androidLibrary {
        namespace = "id.walt.crypto"
    }

    sourceSets {
        commonMain.dependencies {
            // Kotlinx.serialization
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.serialization.cbor)

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
            implementation(identityLibs.kotlincrypto.hash.sha1)
            implementation(identityLibs.kotlincrypto.hash.sha2)
            implementation(identityLibs.kotlincrypto.macs.hmac.sha2)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        val jvmAndroidMain by getting {
            dependencies {
                implementation(identityLibs.tink)
                implementation(identityLibs.bouncycastle.prov)
                implementation(identityLibs.bouncycastle.pkix)
                implementation(identityLibs.nimbus.jose.jwt)
                implementation(identityLibs.kotlinx.serialization.cbor)
                implementation(identityLibs.kotlinx.coroutines.jdk8)
            }
        }
        androidMain {
            dependencies {
                implementation(identityLibs.signum.indispensable)
                implementation(identityLibs.signum.indispensable.josef)
                implementation(identityLibs.signum.supreme)
                implementation(identityLibs.kotlinx.coroutines.android)
                implementation(identityLibs.cryptography.core)
                implementation(identityLibs.cryptography.provider.jdk)
            }
            // Exclude signum's jdk18on BouncyCastle — we use lts8on from jvmAndroidMain
            configurations.all {
                exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
                exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
                exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(identityLibs.kotlinx.coroutines.test)
                implementation("androidx.test.ext:junit:1.2.1")
                implementation("androidx.test:runner:1.6.1")
                implementation("androidx.test:rules:1.6.1")
            }
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.ktor.client.java)
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
            iosMain.dependencies {
                implementation(identityLibs.signum.indispensable)
                implementation(identityLibs.signum.indispensable.josef)
                implementation(identityLibs.signum.supreme)
                implementation(identityLibs.cryptography.core)
                implementation(identityLibs.cryptography.provider.optimal)
                implementation(identityLibs.cryptography.provider.openssl3.prebuilt)
            }
            iosTest.dependencies {
                implementation(kotlin("test"))
                implementation(identityLibs.signum.indispensable)
                implementation(identityLibs.signum.indispensable.josef)
                implementation(identityLibs.signum.supreme)
                implementation(identityLibs.kotlinx.coroutines.test)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto")
        description.set("walt.id crypto library")
    }
}
