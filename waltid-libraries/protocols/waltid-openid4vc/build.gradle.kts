plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.openid4vc"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

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
            // Multiplatform / Uuid
            implementation("app.softwork:kotlinx-uuid-core:0.1.7")

            // Multiplatform / Date & time
            implementation(identityLibs.kotlinx.datetime)

            // Multiplatform / Hashes
            implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.8.0"))
            implementation("org.kotlincrypto.hash:sha2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:${Versions.KTOR_VERSION}")
            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
            implementation("com.nimbusds:nimbus-jose-jwt:10.8")
        }
        jvmTest.dependencies {
            implementation("com.nimbusds:nimbus-jose-jwt:10.8")
            implementation("io.kotest:kotest-runner-junit5:6.1.3")
            implementation("io.kotest:kotest-assertions-core:6.1.3")
            implementation("io.kotest:kotest-assertions-json:6.1.3")
            implementation("org.junit.jupiter:junit-jupiter-params:6.0.3")
            implementation("com.google.crypto.tink:tink:1.20.0") // for JOSE using Ed25519
            implementation("org.bouncycastle:bcprov-lts8on:2.73.10") // for secp256k1 (which was removed with Java 17)
            implementation("org.bouncycastle:bcpkix-lts8on:2.73.10") // PEM import

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

            implementation(identityLibs.slf4j.simple)
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
