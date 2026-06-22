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
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)

        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
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
