plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

dependencies {
    api(project(":waltid-libraries:waltid-library-commons"))
    // OIDC
    api(project(":waltid-libraries:protocols:waltid-openid4vc"))

    api(project(":waltid-libraries:waltid-did"))

    // Ktor
    api("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")

    // Logging
    api("io.klogging:klogging-jvm:0.11.6") // JVM + ~JS
    implementation("io.klogging:slf4j-klogging:0.11.6")
    implementation("org.slf4j:jul-to-slf4j:2.0.17")

    // CLI
    api("com.github.ajalt.clikt:clikt:5.0.3")  // JVM

    // Config
    api("com.sksamuel.hoplite:hoplite-core:2.9.0")
    api("com.sksamuel.hoplite:hoplite-hocon:2.9.0")
    api("com.sksamuel.hoplite:hoplite-hikaricp:2.9.0")

    // Kotlinx.serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Health checks
    api("com.sksamuel.cohort:cohort-ktor:2.6.2")

    // OpenAPI
    api("io.github.smiley4:ktor-openapi:5.3.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.3.0")
    implementation("io.github.smiley4:ktor-redoc:5.3.0")

    implementation("io.github.smiley4:schema-kenerator-core:2.4.0")
    implementation("io.github.smiley4:schema-kenerator-swagger:2.4.0")
    implementation("io.github.smiley4:schema-kenerator-serialization:2.4.0")
    implementation("io.github.smiley4:schema-kenerator-reflection:2.4.0")

    // Persistence
    api("io.github.reactivecircus.cache4k:cache4k:0.14.0")
    api("app.softwork:kotlinx-uuid-core:0.1.6")
    api("redis.clients:jedis:5.2.0")

    // Testing
    testImplementation(identityLibs.bundles.waltid.ktortesting)
}

mavenPublishing {
    pom {
        name.set("walt.id service-commons")
        description.set(
            """
            Kotlin/Java library for the walt.id services-commons
            """.trimIndent()
        )
    }
}
