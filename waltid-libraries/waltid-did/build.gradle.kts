plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.did"


kotlin {

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

            // Web data fetching (provides platform-correct HTTP engine selection + TLS 1.3 on JVM)
            implementation(project(":waltid-libraries:web:waltid-web-data-fetching"))

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Date
            implementation(identityLibs.kotlinx.datetime)

            // Crypto
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:crypto:waltid-crypto2"))

            // Encodings
            implementation(identityLibs.url.encoder)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        val jvmAndroidMain by getting {
            dependencies {
                // Json canonicalization
                implementation(identityLibs.java.json.canonicalization)
            }
        }
        jvmMain.dependencies {
            // Ktor client
            implementation(identityLibs.ktor.client.java)

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
    }
}

mavenPublishing {
    pom {
        name.set("walt.id DID library")
        description.set("walt.id Kotlin/Java library working with Decentralised Identifiers (DIDs)")
    }
}
