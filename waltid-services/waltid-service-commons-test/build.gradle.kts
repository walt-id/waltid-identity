plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    // Testing
    implementation(kotlin("test"))
    implementation("io.ktor:ktor-server-test-host:${Versions.KTOR_VERSION}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-logging:${Versions.KTOR_VERSION}")
}

// Create a configuration for test artefacts
configurations {
    create("testArtifacts") {
        extendsFrom(configurations["testImplementation"])
        isCanBeConsumed = true
        isCanBeResolved = false
    }
}

// Package the test classes in a jar
val testJar by tasks.register<Jar>(Jar::class.toString()) {
    archiveClassifier.set("test")
    from(sourceSets["test"].output)
}

artifacts {
    add("testArtifacts", testJar)
}

mavenPublishing {
    pom {
        name.set("walt.id service-commons-test")
        description.set(
            """
            Kotlin/Java library for walt.id services-commons-test
            """.trimIndent()
        )
    }
}
