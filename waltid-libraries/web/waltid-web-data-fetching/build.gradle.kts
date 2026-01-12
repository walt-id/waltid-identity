plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
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

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            implementation("io.github.reactivecircus.cache4k:cache4k:0.14.0")
            // For in-memory cache
            //implementation("com.mayakapps.kache:kache:2.1.1")
            // For persistent cache
            //implementation("com.mayakapps.kache:file-kache:<version>")
        }
        commonTest.dependencies {
            implementation(identityLibs.bundles.waltid.kotlintesting)
            implementation(identityLibs.ktor.client.cio)
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }
        jsTest.dependencies {
            implementation(identityLibs.ktor.client.js)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Web Data Fetching")
        description.set("walt.id Kotlin/Java Web Data Fetching Library")
    }
}
