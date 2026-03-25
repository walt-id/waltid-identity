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
            implementation(identityLibs.kotlinx.io.core)
            implementation(identityLibs.kotlinx.io.bytestring)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            implementation(identityLibs.optimumcode.jsonschemavalidator)
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
        }
        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.junit.jupiter.params)
            implementation(identityLibs.ktor.server.test.host)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.server.netty)
            implementation("io.mockk:mockk:1.14.9")
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
