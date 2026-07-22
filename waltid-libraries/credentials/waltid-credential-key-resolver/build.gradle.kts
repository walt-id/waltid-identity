plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

kotlin {
    js(IR) {
        outputModuleName.set("credential-key-resolver")
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Ktor HTTP (for URL building in WellKnownKeyResolver)
            implementation(identityLibs.ktor.client.core)

            // walt.id
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(project(":waltid-libraries:crypto:waltid-jose"))
            api(project(":waltid-libraries:waltid-did"))
            api(project(":waltid-libraries:web:waltid-web-data-fetching"))
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.ktor.serialization.kotlinx.json)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.server.netty)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Credential Key Resolver")
        description.set("JWT credential signing key resolution: DID, x5c certificate chain, and HTTPS well-known JWT VC Issuer Metadata")
    }
}
