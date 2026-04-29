plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.credentials"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Serialization
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.kotlinx.coroutines.core)

            // HTTP fetching (KMP Ktor client core)
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Crypto (SHA-256) — multiplatform
            implementation(identityLibs.kotlincrypto.hash.sha2)
        }

        jvmMain.dependencies {
            // OkHttp engine for JVM (SSRF-safe DNS resolver requires OkHttp)
            implementation(identityLibs.ktor.client.okhttp)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
