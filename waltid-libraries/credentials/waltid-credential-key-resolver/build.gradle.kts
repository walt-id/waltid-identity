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
            api(project(":waltid-libraries:waltid-did"))
            api(project(":waltid-libraries:web:waltid-web-data-fetching"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            // X.509 certificate chain validation (AKI/SKI, signature chain)
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
        }
        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Credential Key Resolver")
        description.set("JWT credential signing key resolution: DID, x5c certificate chain, and HTTPS well-known JWT VC Issuer Metadata")
    }
}
