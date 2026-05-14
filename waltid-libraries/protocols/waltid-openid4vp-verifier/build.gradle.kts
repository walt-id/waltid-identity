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
            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

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
        }

        jvmMain.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-cose"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }

        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
            implementation(identityLibs.ktor.server.test.host)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Verifier SDK - OpenID4VP version")
        description.set("walt.id Kotlin/Java Verifier for OpenID4VP")
    }
}
