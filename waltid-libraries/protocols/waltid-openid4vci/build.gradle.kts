plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.openid4vci"

object Versions {
    const val KTOR_VERSION = "3.3.3"
    const val COROUTINES_VERSION = "1.10.2"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")

            // walt.id
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)
            implementation("io.ktor:ktor-http:${Versions.KTOR_VERSION}")

            implementation("io.github.oshai:kotlin-logging:7.0.13")

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            // -- Multiplatform --
            // Multiplatform / Uuid
            implementation("app.softwork:kotlinx-uuid-core:0.1.6")

            // Multiplatform / Date & time
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            // Multiplatform / Hashes
            implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
            implementation("org.kotlincrypto.hash:sha2")

            // Multiplatform / Secure Random
            implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")

            implementation("org.jetbrains.kotlinx:atomicfu:0.24.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES_VERSION}")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:${Versions.KTOR_VERSION}")
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
        }
        jsMain.dependencies {
            implementation(npm("jose", "5.10.0"))
            implementation("io.ktor:ktor-client-js:${Versions.KTOR_VERSION}")
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
