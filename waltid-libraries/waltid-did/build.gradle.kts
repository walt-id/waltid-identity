plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.did"


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
            implementation(identityLibs.ktor.client.java)

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
            implementation(identityLibs.ktor.server.test.host)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.server.netty)
            implementation(identityLibs.ktor.network.tls.certificates)
        }
        jsMain.dependencies {
            implementation(identityLibs.ktor.client.js)

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
