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
            implementation(identityLibs.optimumcode.jsonschemavalidator)

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

        if (enableIosBuild) {
            iosMain.dependencies {
                implementation(identityLibs.ktor.client.darwin)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.bouncycastle.prov)
            implementation(identityLibs.bouncycastle.pkix)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
