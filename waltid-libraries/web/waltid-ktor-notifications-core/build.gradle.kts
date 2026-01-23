plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.web"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Kotlinx
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("app.softwork:kotlinx-uuid-core:0.1.6")

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Ktor Server Notifications Library - Core")
        description.set("walt.id Kotlin/Java Ktor Server Notifications Library - Core")
    }
}
