plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.did"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    applyDefaultHierarchyTemplate()

    js(IR) {
        outputModuleName = "dids"
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Date
            implementation(identityLibs.kotlinx.datetime)

            // Uuid
            implementation("app.softwork:kotlinx-uuid-core:0.1.7")

            // Crypto
            api(project(":waltid-libraries:crypto:waltid-crypto"))

            // Encodings
            implementation("net.thauvin.erik.urlencoder:urlencoder-lib:1.6.0")

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            // Ktor client
            implementation("io.ktor:ktor-client-okhttp:${Versions.KTOR_VERSION}")

            // Json canonicalization
            implementation("io.github.erdtman:java-json-canonicalization:1.1")

            // Multiformat
            // implementation("com.github.multiformats:java-multibase:v1.1.1")
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)


            implementation(identityLibs.kotlinx.serialization.json)
            implementation(kotlin("test"))
            implementation(identityLibs.junit.jupiter.params)
            implementation("io.ktor:ktor-server-test-host:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-content-negotiation:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-netty:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-network-tls-certificates:${Versions.KTOR_VERSION}")
        }
        jsMain.dependencies {
            implementation("io.ktor:ktor-client-js:${Versions.KTOR_VERSION}")

            implementation(npm("canonicalize", "2.0.0"))
            implementation(npm("uuid", "9.0.1"))
        }
        if (enableIosBuild) {
            iosMain.dependencies { }
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id DID library")
        description.set("walt.id Kotlin/Java library working with Decentralised Identifiers (DIDs)")
    }
}
