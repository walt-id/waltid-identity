plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.openid4vc"


fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    applyDefaultHierarchyTemplate()
    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
        commonMain.dependencies {
            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)

            implementation(identityLibs.oshai.kotlinlogging)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            // walt.id
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-verification-policies"))
            implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            implementation(project(":waltid-libraries:waltid-did"))

            // -- Multiplatform --

            // Multiplatform / Date & time
            implementation(identityLibs.kotlinx.datetime)

            // Multiplatform / Hashes

            implementation(identityLibs.kotlincrypto.hash.sha2)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(identityLibs.ktor.client.java)
            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
            implementation(identityLibs.nimbus.jose.jwt)
        }
        jvmTest.dependencies {
            implementation(identityLibs.nimbus.jose.jwt)
            implementation("io.kotest:kotest-runner-junit5:6.1.11") // should be replaced
            implementation("io.kotest:kotest-assertions-core:6.1.11") // should be replaced
            implementation("io.kotest:kotest-assertions-json:6.1.11") // should be replaced
            implementation(identityLibs.junit.jupiter.params)
            implementation(identityLibs.tink) // for JOSE using Ed25519
            implementation(identityLibs.bouncycastle.prov) // for secp256k1 (which was removed with Java 17)
            implementation(identityLibs.bouncycastle.pkix) // PEM import

            implementation(identityLibs.ktor.server.core)
            implementation(identityLibs.ktor.server.netty)
            implementation(identityLibs.ktor.server.status.pages)
            implementation(identityLibs.ktor.server.default.headers)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.ktor.client.java)
            implementation(identityLibs.ktor.client.cio)
            implementation(identityLibs.ktor.client.auth)
            implementation(identityLibs.ktor.client.content.negotiation)
            implementation(identityLibs.ktor.serialization.kotlinx.json)
            implementation(identityLibs.ktor.client.logging)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.11.0")

            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
            implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")

            implementation(identityLibs.slf4j.simple)
        }
        jsMain.dependencies {
            implementation(npm("jose", "5.10.0"))
            implementation(identityLibs.ktor.client.js)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id OpenId4VC library")
        description.set("walt.id Kotlin/Java OpenId4VC library")
    }
}
