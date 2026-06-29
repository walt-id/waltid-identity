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
            implementation(identityLibs.jsonpathkt)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.optimumcode.jsonschemavalidator)

            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.slf4j.simple)
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
