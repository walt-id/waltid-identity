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
            implementation(identityLibs.jsonpathkt)

            /*
             * walt.id:
             */
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
            implementation(project(":waltid-libraries:crypto:waltid-cose"))
            implementation(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:credentials:waltid-vical"))
            implementation(project(":waltid-libraries:credentials:waltid-dcql"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto")) // for Base64Utils
            implementation(project(":waltid-libraries:web:waltid-web-data-fetching"))

            implementation(identityLibs.korlibs.io)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
        }
        jvmMain.dependencies {
            // Trust Registry library for inline trust list resolution
            implementation(project(":waltid-libraries:credentials:waltid-trust-registry"))
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.junit.jupiter.params)
            implementation(identityLibs.ktor.server.test.host)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.server.netty)
            implementation(identityLibs.mockk)
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
