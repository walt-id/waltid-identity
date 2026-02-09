plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.dif-definitions-parser"

kotlin {
    js(IR) {
        outputModuleName = "definitions-parser"
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.github.optimumcode:json-schema-validator:0.4.0")

            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id DIF Definitions Parser")
        description.set(
            """
            Kotlin/Java library for DIF definitions parsing
            """.trimIndent()
        )
    }
}
