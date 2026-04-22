plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

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

            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

            // CBOR
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
            implementation(identityLibs.kotlinx.serialization.cbor)

            // Crypto
            implementation("org.kotlincrypto.random:crypto-rand:0.6.0") // SecureRandom

            implementation(identityLibs.kotlincrypto.hash.sha2) // SHA-224, SHA-256, SHA-384, SHA-512, SHA-512/t, SHA-512/224, SHA-512/256
            implementation(identityLibs.kotlincrypto.macs.hmac.sha2)

            /*
             * walt.id:
             */
            api(project(":waltid-libraries:crypto:waltid-cose"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
            implementation(project(":waltid-libraries:credentials:waltid-verification-policies2-vp"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            implementation(project(":waltid-libraries:protocols:waltid-openid4vp"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Verification Policies")
        description.set("walt.id Verification Policies for Kotlin/Java")
    }
}
