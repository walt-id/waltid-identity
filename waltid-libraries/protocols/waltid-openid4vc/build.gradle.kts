plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.openid4vc"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

kotlin {

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
        commonMain.dependencies {
            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)

            implementation(identityLibs.oshai.kotlinlogging)

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            // walt.id
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-verification-policies"))
            implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            implementation(project(":waltid-libraries:waltid-did"))

            // -- Multiplatform --
            // Multiplatform / Uuid
            implementation("app.softwork:kotlinx-uuid-core:0.1.6")

            // Multiplatform / Date & time
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            // Multiplatform / Hashes
            implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
            implementation("org.kotlincrypto.hash:sha2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:${Versions.KTOR_VERSION}")
            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
            implementation("com.nimbusds:nimbus-jose-jwt:10.6")
        }
        jvmTest.dependencies {
            implementation("com.nimbusds:nimbus-jose-jwt:10.6")
            implementation("io.kotest:kotest-runner-junit5:5.9.1")
            implementation("io.kotest:kotest-assertions-core:5.9.1")
            implementation("io.kotest:kotest-assertions-json:5.9.1")
            implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
            implementation("com.google.crypto.tink:tink:1.16.0") // for JOSE using Ed25519
            implementation("org.bouncycastle:bcprov-lts8on:2.73.8") // for secp256k1 (which was removed with Java 17)
            implementation("org.bouncycastle:bcpkix-lts8on:2.73.8") // PEM import

            implementation("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-netty-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-default-headers-jvm:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-content-negotiation:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-core:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-cio:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-auth:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.10.2")
            implementation("io.ktor:ktor-client-okhttp:${Versions.KTOR_VERSION}")

            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
            implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")

            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
        jsMain.dependencies {
            implementation(npm("jose", "5.10.0"))
            implementation("io.ktor:ktor-client-js:${Versions.KTOR_VERSION}")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id OpenId4VC library")
        description.set("walt.id Kotlin/Java OpenId4VC library")
    }
}
