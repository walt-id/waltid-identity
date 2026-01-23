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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Kotlinx
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("app.softwork:kotlinx-uuid-core:0.1.6")

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

            // CBOR
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.9.0")
            implementation("net.orandja.obor:obor:2.1.3")

            // Crypto
            implementation("org.kotlincrypto.random:crypto-rand:0.5.2") // SecureRandom

            implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.7.1"))
            implementation("org.kotlincrypto.hash:sha2") // SHA-224, SHA-256, SHA-384, SHA-512, SHA-512/t, SHA-512/224, SHA-512/256

            implementation(project.dependencies.platform("org.kotlincrypto.macs:bom:0.7.1"))
            implementation("org.kotlincrypto.macs:hmac-sha2")

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
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Verification Policies")
        description.set("walt.id Verification Policies for Kotlin/Java")
    }
}
