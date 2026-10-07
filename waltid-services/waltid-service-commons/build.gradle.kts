plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
    alias(identityLibs.plugins.buildconfig)
}

group = "id.walt"


dependencies {
    api(project(":waltid-libraries:waltid-library-commons"))

    api(project(":waltid-libraries:web:waltid-web-data-fetching"))

    api(project(":waltid-libraries:waltid-did"))

    // Ktor
    api(identityLibs.ktor.server.core)
    api(identityLibs.ktor.server.cio)
    api(identityLibs.ktor.server.status.pages)
    api(identityLibs.ktor.server.content.negotiation)
    api(identityLibs.ktor.serialization.kotlinx.json)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.client.java)

    // Logging
    api(identityLibs.klogging) // JVM + ~JS
    implementation(identityLibs.slf4j.klogging)
    implementation(identityLibs.slf4j.julbridge)

    // CLI
    api(identityLibs.clikt.core)

    // Config
    api(identityLibs.hoplite.core)
    api(identityLibs.hoplite.hocon)
    api(identityLibs.hoplite.hikaricp)

    // Kotlinx.serialization
    api(identityLibs.kotlinx.serialization.json)

    // Health checks
    api(identityLibs.sksamuel.cohort)

    // OpenAPI
    api(identityLibs.smiley4.service.openapi)
    implementation(identityLibs.smiley4.service.swagger.ui)
    implementation(identityLibs.smiley4.service.redoc)
    implementation(libs.bundles.smiley4.schema.kenerator)

    // Persistence
    api(identityLibs.cache4k)
    api(identityLibs.jedis)

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
