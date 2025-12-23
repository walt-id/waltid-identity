plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.permissions"


kotlin {
    js(IR) {
        outputModuleName = "waltid-permissions"
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id permissions")
        description.set(
            """
            Kotlin/Java library for permissions
            """.trimIndent()
        )
    }
}

tasks.named("jsBrowserTest") {
    enabled = false
}
