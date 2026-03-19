plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.openid4vci"


kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // walt.id
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)
            implementation(identityLibs.ktor.http)

            implementation(identityLibs.oshai.kotlinlogging)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            // -- Multiplatform --

            // Multiplatform / Date & time
            implementation(identityLibs.kotlinx.datetime)

            // Multiplatform / Hashes
            implementation(identityLibs.kotlincrypto.hash.sha2)

            // Multiplatform / Secure Random
            implementation(identityLibs.kotlincrypto.random)

            implementation(identityLibs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(identityLibs.ktor.client.okhttp)
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation(identityLibs.junit.jupiter.api)
        }
        jsMain.dependencies {
            implementation(npm("jose", "5.10.0"))
            implementation(identityLibs.ktor.client.js)
        }
        jsTest.dependencies {
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id OpenID4VCI library")
        description.set("walt.id Kotlin/Java OpenID4VCI library")
    }
}
