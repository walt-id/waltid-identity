plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.web"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)
            implementation(identityLibs.ktor.client.cio)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            implementation("io.github.reactivecircus.cache4k:cache4k:0.14.0")
            // For in-memory cache
            //implementation("com.mayakapps.kache:kache:2.1.1")
            // For persistent cache
            //implementation("com.mayakapps.kache:file-kache:<version>")
        }
        commonTest.dependencies {
            implementation(identityLibs.bundles.waltid.kotlintesting)
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }

        jvmMain.dependencies {
            implementation(identityLibs.ktor.client.java)
            implementation(identityLibs.ktor.client.apache5)
            implementation(identityLibs.ktor.client.okhttp)
            //implementation(identityLibs.ktor.client.jetty)
        }

        /* To do:
        androidMain.dependencies {
            implementation(identityLibs.ktor.client.android)
            implementation(identityLibs.ktor.client.okhttp)
        }
         */


        iosMain.dependencies {
            implementation(identityLibs.ktor.client.darwin)
        }
        macosMain.dependencies {
            implementation(identityLibs.ktor.client.darwin)
        }

        linuxMain.dependencies {
            implementation(identityLibs.ktor.client.curl)
        }

        mingwMain.dependencies {
            implementation(identityLibs.ktor.client.winhttp)
        }

        jsMain.dependencies {
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
