plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt"

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

mavenPublishing {
    pom {
        name.set("walt.id java compatability helpers")
        description.set(
            """
            Kotlin/Java helper library to make it easier to use certain functions from Java.
            """.trimIndent()
        )
    }
}
