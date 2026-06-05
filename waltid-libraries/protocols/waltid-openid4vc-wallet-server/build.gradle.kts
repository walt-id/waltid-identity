plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.protocols"

dependencies {
    // The base wallet library — all protocol logic
    api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))

    // Service commons (WaltConfig, ServiceFeatureCatalog, etc.)
    api(project(":waltid-services:waltid-service-commons"))

    // Ktor server — route handlers, content negotiation, status pages
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.server.double.receive)
    implementation(identityLibs.ktor.server.status.pages)

    // OpenAPI / Swagger UI doc descriptors
    implementation(identityLibs.smiley.ktor.openapi)
    implementation(identityLibs.smiley.ktor.swaggerui)

    // Serialization
    implementation(identityLibs.ktor.serialization.kotlinx.json)
    implementation(identityLibs.kotlinx.serialization.json)

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)

    // Tests
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
}

mavenPublishing {
    pom {
        name.set("walt.id Wallet SDK - Server library (Ktor route handlers + OpenAPI)")
        description.set(
            "Shared Ktor route handlers and OpenAPI documentation for the walt.id Wallet2 " +
                "backend. Used by both the OSS wallet service and the Enterprise wallet service."
        )
    }
}
