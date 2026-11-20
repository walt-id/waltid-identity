plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.protocols"

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // HTTP
            implementation(identityLibs.ktor.client.core)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.optimumcode.jsonschemavalidator)

            /*
             * walt.id:
             */
            api(project(":waltid-libraries:waltid-library-commons"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:credentials:waltid-dcql"))
        }
        commonTest.dependencies {
            implementation(identityLibs.bundles.waltid.kotlintesting)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id OpenID4VP library")
        description.set("walt.id OpenID4VP library for Kotlin/Java")
    }
}
