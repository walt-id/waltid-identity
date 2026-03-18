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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)
            implementation(identityLibs.slf4j.simple)

            // Kotlinx
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")


            implementation(identityLibs.kotlincrypto.hash.sha2)
            /*
             * walt.id dependencies:
             */
            // OpenID4VCI shared protocol models
            implementation(project(":waltid-libraries:protocols:waltid-openid4vci"))

            // Crypto for key operations and proof signing
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
        }
        commonTest.dependencies {
            implementation(identityLibs.bundles.waltid.ktortesting)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Wallet SDK - OpenID4VCI version")
        description.set("walt.id Kotlin/Java Wallet for OpenID4VCI")
    }
}