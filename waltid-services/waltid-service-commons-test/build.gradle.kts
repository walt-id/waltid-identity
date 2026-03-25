plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt"


dependencies {

    configurations.all {
        exclude(group = "io.ktor", module = "ktor-client-cio")
    }

    api(project(":waltid-services:waltid-service-commons"))
    api(identityLibs.ktor.server.test.host)

    // Testing
    implementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    implementation(identityLibs.ktor.client.cio)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.serialization.kotlinx.json)
    implementation(identityLibs.ktor.client.logging)
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
