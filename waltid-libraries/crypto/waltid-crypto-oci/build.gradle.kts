plugins {
    id("waltid.multiplatform.library.jvm")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // Crypto
            implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
            implementation("org.kotlincrypto.hash:sha2")

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
            implementation("com.oracle.oci.sdk:oci-java-sdk-shaded-full:3.57.1")

            // JOSE
            implementation("com.nimbusds:nimbus-jose-jwt:10.6")

            // walt.id
            api(project(":waltid-libraries:crypto:waltid-crypto"))
        }
        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            // Logging
            implementation("org.slf4j:slf4j-simple:2.0.17")

            // Test
            implementation(kotlin("test"))
            implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
            implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto OCI library")
        description.set("walt.id Kotlin/Java crypto OCI library")
    }
}
