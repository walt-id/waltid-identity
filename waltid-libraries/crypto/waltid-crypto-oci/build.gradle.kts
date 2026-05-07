plugins {
    id("waltid.multiplatform.library.jvm")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Crypto

            implementation(identityLibs.kotlincrypto.hash.sha2)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // walt.id
            api(project(":waltid-libraries:crypto:waltid-crypto"))
        }
        commonTest.dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")

            // OCI
            implementation("com.oracle.oci.sdk:oci-java-sdk-shaded-full:3.86.1")

            // JOSE
            implementation(identityLibs.nimbus.jose.jwt)

            // walt.id
            api(project(":waltid-libraries:crypto:waltid-crypto"))
        }
        jvmTest.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)

            // Logging
            implementation("org.slf4j:slf4j-simple:2.0.17")

            // Test
            implementation(kotlin("test"))
            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.junit.jupiter.params)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto OCI library")
        description.set("walt.id Kotlin/Java crypto OCI library")
    }
}
