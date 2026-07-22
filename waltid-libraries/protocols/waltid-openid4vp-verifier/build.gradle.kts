plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.protocols"


kotlin {

    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // HTTP
            implementation(identityLibs.ktor.server.core)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Kotlinx
            implementation(identityLibs.kotlinx.datetime)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            implementation(identityLibs.optimumcode.jsonschemavalidator)
            implementation(identityLibs.jsonpathkt)

            // CBOR
            implementation(identityLibs.kotlinx.serialization.cbor)

            /*
             * walt.id:
             */
            implementation(project(":waltid-libraries:protocols:waltid-openid4vp"))
            implementation(project(":waltid-libraries:credentials:waltid-dcql"))
            implementation(project(":waltid-libraries:credentials:waltid-verification-policies2"))
            implementation(project(":waltid-libraries:credentials:waltid-verification-policies2-vp"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            implementation(project(":waltid-libraries:web:waltid-ktor-notifications-core"))

            implementation(project(":waltid-libraries:protocols:waltid-18013-7-verifier"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(project(":waltid-libraries:crypto:waltid-jose"))
            implementation(project(":waltid-libraries:waltid-did"))
        }

        jvmMain.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-cose"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.ktor.server.test.host)
            implementation(project(":waltid-libraries:crypto:waltid-crypto2-migration-v1"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Verifier SDK - OpenID4VP version")
        description.set("walt.id Kotlin/Java Verifier for OpenID4VP")
    }
}
