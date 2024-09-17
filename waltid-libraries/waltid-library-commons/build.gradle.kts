plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization")
    id("maven-publish")
}

group = "id.walt"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

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
