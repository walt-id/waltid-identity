plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.dcql"

kotlin {
    js(IR) {
        outputModuleName = "dcql"
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id DCQL library")
        description.set(
            """
            Kotlin/Java library for DCQL matching
            """.trimIndent()
        )
    }
}
