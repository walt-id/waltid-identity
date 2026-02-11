import io.ktor.plugin.features.*

object Versions {
    const val KTOR_VERSION = "3.3.3"
    const val COROUTINES_VERSION = "1.10.2"
    const val HOPLITE_VERSION = "2.9.0"
}

plugins {
    id("waltid.ktorbackend")
    id("waltid.ktordocker")
}

group = "id.walt"

dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-sessions-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auto-head-response-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-double-receive-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-host-common-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-compression-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-cors-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-call-logging-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-call-id-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")

    // Ktor client
    implementation("io.ktor:ktor-client-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-json-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.5")
    implementation("org.slf4j:jul-to-slf4j:2.0.17")
    implementation("io.klogging:klogging-jvm:0.11.6")
    implementation("io.klogging:slf4j-klogging:0.11.6")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES_VERSION}")

    // OIDC
    api(project(":waltid-libraries:protocols:waltid-openid4vc"))

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-azure"))

    api(project(":waltid-libraries:waltid-did"))

    api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
    api(project(":waltid-libraries:credentials:waltid-verification-policies"))
    api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))

    // crypto
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
    implementation("com.nimbusds:nimbus-jose-jwt:10.6")
    // Bouncy Castle
    implementation("org.bouncycastle:bcprov-lts8on:2.73.8")
    implementation("org.bouncycastle:bcpkix-lts8on:2.73.8")

    // Multiplatform / Hashes
    testImplementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
    testImplementation("org.kotlincrypto.hash:sha2")
}

application {
    mainClass.set("id.walt.issuer.MainKt")
}

ktor {
    docker {
        portMappings.set(
            listOf(
                DockerPortMapping(7002, 7002, DockerPortMappingProtocol.TCP)
            )
        )
    }
}
