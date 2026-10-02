plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt"

dependencies {
    api(identityLibs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

mavenPublishing {
    pom {
        name.set("walt.id service-events")
        description.set(
            """
            Kotlin/Java library for the event types of the walt.id services
            """.trimIndent()
        )
    }
}
