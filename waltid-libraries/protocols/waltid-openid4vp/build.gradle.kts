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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // HTTP
            implementation(identityLibs.ktor.client.core)

            // Logging
            implementation("io.github.oshai:kotlin-logging:7.0.13")

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            /*
             * walt.id:
             */
            implementation(project(":waltid-libraries:credentials:waltid-dcql"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id OpenID4VP library")
        description.set("walt.id OpenID4VP library for Kotlin/Java")
    }
}
