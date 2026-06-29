plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"
fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    applyDefaultHierarchyTemplate()
    js(IR) {
        outputModuleName.set("w3c-credentials")
    }
    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.optimumcode.jsonschemavalidator)

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Kotlinx
            implementation(identityLibs.kotlinx.datetime)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // walt.id
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            api(project(":waltid-libraries:waltid-did"))
            api(project(":waltid-libraries:web:waltid-web-data-fetching"))
            api(project(":waltid-libraries:credentials:waltid-credential-key-resolver"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)
            implementation(identityLibs.ktor.client.cio)
        }
        jvmMain.dependencies {
        }
        jvmTest.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.slf4j.simple)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id W3C credential")
        description.set("walt.id Kotlin/Java library for W3C Credentials")
    }
}
