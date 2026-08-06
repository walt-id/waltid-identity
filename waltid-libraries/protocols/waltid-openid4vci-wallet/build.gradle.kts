plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.protocols"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Kotlinx
            implementation(identityLibs.kotlinx.datetime)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)


            implementation(identityLibs.kotlincrypto.hash.sha2)
            /*
             * walt.id dependencies:
             */
            // OpenID4VCI shared protocol models
            implementation(project(":waltid-libraries:protocols:waltid-openid4vci"))

            // Crypto for key operations and proof signing
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(project(":waltid-libraries:crypto:waltid-jose"))
        }
        commonTest.dependencies {
            implementation(identityLibs.bundles.waltid.ktortesting)
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Wallet SDK - OpenID4VCI version")
        description.set("walt.id Kotlin/Java Wallet for OpenID4VCI")
    }
}
