plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
    id("com.github.gmazzo.buildconfig") version "5.4.0" // Add this line
}

group = "id.walt"


dependencies {
    api(project(":waltid-libraries:waltid-library-commons"))
    // OIDC
    api(project(":waltid-libraries:protocols:waltid-openid4vc"))

    api(project(":waltid-libraries:waltid-did"))

    // Ktor
    api(identityLibs.ktor.server.core)
    api(identityLibs.ktor.server.cio)
    api(identityLibs.ktor.server.status.pages)
    api(identityLibs.ktor.server.content.negotiation)
    api(identityLibs.ktor.serialization.kotlinx.json)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.client.okhttp)

    // Logging
    api(identityLibs.klogging) // JVM + ~JS
    implementation(identityLibs.slf4j.klogging)
    implementation(identityLibs.slf4j.julbridge)

    // CLI
    api(identityLibs.clikt.core)

    // Config
    api("com.sksamuel.hoplite:hoplite-core:2.9.0")
    api("com.sksamuel.hoplite:hoplite-hocon:2.9.0")
    api("com.sksamuel.hoplite:hoplite-hikaricp:2.9.0")

    // Kotlinx.serialization
    api(identityLibs.kotlinx.serialization.json)

    // Health checks
    api(identityLibs.sksamuel.cohort)

    // OpenAPI
    api("io.github.smiley4:ktor-openapi:5.6.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.6.0")
    implementation("io.github.smiley4:ktor-redoc:5.6.0")

    implementation("io.github.smiley4:schema-kenerator-core:2.6.0")
    implementation("io.github.smiley4:schema-kenerator-swagger:2.6.0")
    implementation("io.github.smiley4:schema-kenerator-serialization:2.6.0")
    implementation("io.github.smiley4:schema-kenerator-reflection:2.6.0")

    // Persistence
    api("io.github.reactivecircus.cache4k:cache4k:0.14.0")
    api("redis.clients:jedis:7.4.0")

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
