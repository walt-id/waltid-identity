plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.policies"


fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    js(IR) {
        outputModuleName = "verification-policies"
    }

    applyDefaultHierarchyTemplate()
    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")
            // JSON
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.optimumcode.jsonschemavalidator)

            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-dif-definitions-parser"))
            implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))//for Base64Utils
            implementation(project(":waltid-libraries:crypto:waltid-cose"))

            // Kotlinx
            implementation(identityLibs.kotlinx.datetime)

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Loggin
            implementation(identityLibs.oshai.kotlinlogging)

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            implementation("com.soywiz:korlibs-io:6.0.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.junit.jupiter.params)
            implementation(identityLibs.ktor.server.test.host)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.server.netty)
            implementation("io.mockk:mockk:1.14.9")
        }
        if (enableIosBuild) {
            iosMain.dependencies {}
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
