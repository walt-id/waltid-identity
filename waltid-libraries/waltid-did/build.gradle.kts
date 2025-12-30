plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.did"

kotlin {
    js(IR) {
        outputModuleName = "dids"
    }

    val ktor_version = "3.3.3"
    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // Date
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            // Uuid
            implementation("app.softwork:kotlinx-uuid-core:0.1.6")

            // Crypto
            api(project(":waltid-libraries:crypto:waltid-crypto"))

            // Encodings
            implementation("net.thauvin.erik.urlencoder:urlencoder-lib:1.6.0")

            // Logging
            implementation("io.github.oshai:kotlin-logging:7.0.13")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            // Ktor client
            implementation("io.ktor:ktor-client-okhttp:$ktor_version")

            // Json canonicalization
            implementation("io.github.erdtman:java-json-canonicalization:1.1")

            // Multiformat
            // implementation("com.github.multiformats:java-multibase:v1.1.1")
        }
        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")


            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation(kotlin("test"))
            implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
            implementation("io.ktor:ktor-server-test-host:$ktor_version")
            implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
            implementation("io.ktor:ktor-server-netty:$ktor_version")
            implementation("io.ktor:ktor-network-tls-certificates:${ktor_version}")
        }
        jsMain.dependencies {
            implementation("io.ktor:ktor-client-js:$ktor_version")

            implementation(npm("canonicalize", "2.0.0"))
            implementation(npm("uuid", "9.0.1"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id DID library")
        description.set("walt.id Kotlin/Java library working with Decentralised Identifiers (DIDs)")
    }
}
