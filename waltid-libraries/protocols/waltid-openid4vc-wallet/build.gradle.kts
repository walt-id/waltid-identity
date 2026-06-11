plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.protocols"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Ktor client — outbound HTTP only (token requests, credential requests, VP submission)
            // No Ktor server dependency; this library has no concept of HTTP routes.
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Serialization
            implementation(identityLibs.kotlinx.serialization.json)

            // Date/time
            implementation(identityLibs.kotlinx.datetime)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            /*
             * walt.id protocol libraries (all multiplatform)
             */

            // OpenID4VCI 1.0 — shared protocol types (CredentialOffer, metadata, etc.)
            api(project(":waltid-libraries:protocols:waltid-openid4vci"))
            // OpenID4VCI 1.0 — wallet-side client logic (offer parsing, token, proof)
            // package: id.waltid.openid4vci.wallet.*
            api(project(":waltid-libraries:protocols:waltid-openid4vci-wallet"))

            // OpenID4VP 1.0 — core protocol types (AuthorizationRequest, response modes, etc.)
            // package: id.walt.verifier.openid.*
            api(project(":waltid-libraries:protocols:waltid-openid4vp"))
            // OpenID4VP 1.0 — wallet-side presentation logic (DCQL matching, per-format presenters)
            // package: id.waltid.openid4vp.wallet.*
            api(project(":waltid-libraries:protocols:waltid-openid4vp-wallet"))

            // Credential types
            api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))

            // DCQL
            api(project(":waltid-libraries:credentials:waltid-dcql"))

            // Holder-side policies
            api(project(":waltid-libraries:credentials:waltid-holder-policies"))

            // Cryptography and DID
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
        }

        jvmMain.dependencies {
            // These libraries currently have JVM-only implementations
            api(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
            api(project(":waltid-libraries:crypto:waltid-cose"))
            api(project(":waltid-libraries:crypto:waltid-x509"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Wallet SDK - OpenID4VCI + OpenID4VP wallet library")
        description.set("walt.id Kotlin Multiplatform wallet library for OpenID4VCI 1.0 credential issuance and OpenID4VP 1.0 credential presentation")
    }
}
