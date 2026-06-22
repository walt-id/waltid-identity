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
            implementation(identityLibs.ktor.client.core)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            /*
             * walt.id:
             */
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
