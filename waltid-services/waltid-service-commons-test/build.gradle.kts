plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")

    id("com.github.ben-manes.versions") version "0.51.0"
}

group = "id.walt"

repositories {
    mavenLocal()
    mavenCentral()
}

object Versions {
    const val KTOR_VERSION = "2.3.12" // also change 1 plugin
}

dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    // Testing
    api(kotlin("test"))
    api("io.ktor:ktor-server-test-host:${Versions.KTOR_VERSION}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-logging:${Versions.KTOR_VERSION}")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}


// Create a configuration for test artifacts
configurations {
    create("testArtifacts") {
        extendsFrom(configurations["testImplementation"])
        isCanBeConsumed = true
        isCanBeResolved = false
    }
}

// Package the test classes in a jar
val testJar by tasks.creating(Jar::class) {
    archiveClassifier.set("test")
    from(sourceSets["test"].output)
}

artifacts {
    add("testArtifacts", testJar)
}

publishing {
    publications {
        // Main sources
        create<MavenPublication>("mavenJava") {
            pom {
                name.set("walt.id service-commons")
                description.set(
                    """
                    Kotlin/Java library for the walt.id services-commons
                    """.trimIndent()
                )
                url.set("https://walt.id")
            }
            from(components["java"])
        }
    }

    repositories {
        maven {
            val releasesRepoUrl = uri("https://maven.waltid.dev/releases")
            val snapshotsRepoUrl = uri("https://maven.waltid.dev/snapshots")
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            val envUsername = System.getenv("MAVEN_USERNAME")
            val envPassword = System.getenv("MAVEN_PASSWORD")

            val usernameFile = File("secret_maven_username.txt")
            val passwordFile = File("secret_maven_password.txt")

            val secretMavenUsername = envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" }
            val secretMavenPassword = envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }

            credentials {
                username = secretMavenUsername
                password = secretMavenPassword
            }
        }
    }
}
