import org.gradle.kotlin.dsl.invoke

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
}

group = "id.walt"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}
object Versions {
    const val KTOR_VERSION = "2.3.12" // also change 1 plugin
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    // Ktor
    api("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")

    // Logging
    api("io.klogging:klogging-jvm:0.7.0") // JVM + ~JS
    implementation("io.klogging:slf4j-klogging:0.7.0")
    implementation("org.slf4j:jul-to-slf4j:2.0.13")

    // Kotlinx.serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
}

tasks.test {
    useJUnitPlatform()
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                name.set("walt.id library-commons")
                description.set(
                    """
                    Kotlin/Java library for the walt.id library-commons
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
