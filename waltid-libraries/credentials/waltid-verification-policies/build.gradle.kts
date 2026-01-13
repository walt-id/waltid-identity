plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.policies"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

kotlin {
    js(IR) {
        outputModuleName = "verification-policies"
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")
            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.github.optimumcode:json-schema-validator:0.4.0")

            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-dif-definitions-parser"))
            implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))//for Base64Utils

            // Kotlinx
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Loggin
            implementation(identityLibs.oshai.kotlinlogging)

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            
            

            implementation("com.soywiz:korlibs-io:6.0.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.slf4j:slf4j-simple:2.0.17")
            implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
            implementation("io.ktor:ktor-server-test-host:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-content-negotiation:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-server-netty:${Versions.KTOR_VERSION}")
            implementation("io.mockk:mockk:1.14.2")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id verification policies")
        description.set(
            """
            Kotlin/Java library for Verification Policies
            """.trimIndent()
        )
    }
}
