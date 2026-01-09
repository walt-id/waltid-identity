plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

kotlin {
    js(IR) {
        outputModuleName.set("w3c-credentials")
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.github.optimumcode:json-schema-validator:0.4.0")

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // Kotlinx
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("app.softwork:kotlinx-uuid-core:0.1.6")

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // walt.id
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            api(project(":waltid-libraries:waltid-did"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            // Ktor client
            implementation(identityLibs.ktor.client.okhttp)

            // Json canonicalization
            implementation("io.github.erdtman:java-json-canonicalization:1.1")
        }
        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id W3C credential")
        description.set("walt.id Kotlin/Java library for W3C Credentials")
    }
}
