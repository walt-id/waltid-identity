plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

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

            implementation("io.github.optimumcode:json-schema-validator:0.5.2")
            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

            /*
             * walt.id:
             */
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
            implementation(project(":waltid-libraries:credentials:waltid-vical"))
            implementation(project(":waltid-libraries:credentials:waltid-dcql"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto")) // for Base64Utils
            implementation(project(":waltid-libraries:web:waltid-web-data-fetching"))

            implementation("com.soywiz:korlibs-io:6.0.2")

            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
        }
        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
            implementation("io.ktor:ktor-server-test-host:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-content-negotiation:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-netty:${Versions.KTOR_VERSION}")
            implementation("io.mockk:mockk:1.14.2")
        }
    }
}

/* -- Publishing -- */
mavenPublishing {
    pom {
        name.set("walt.id Verification Policies")
        description.set("walt.id Verification Policies for Kotlin/Java")
    }
}
