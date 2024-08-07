plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "id.walt"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}


publishing {
    repositories {
        val envUsername = System.getenv("MAVEN_USERNAME")
        val envPassword = System.getenv("MAVEN_PASSWORD")
        val usernameFile = File("secret_maven_username.txt")
        val passwordFile = File("secret_maven_password.txt")
        val secretMavenUsername =
            envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" }
        val secretMavenPassword =
            envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }
        val hasMavenAuth = secretMavenUsername.isNotEmpty() && secretMavenPassword.isNotEmpty()
        if (hasMavenAuth) {
            maven {
                val releasesRepoUrl = uri("https://maven.waltid.dev/releases")
                val snapshotsRepoUrl = uri("https://maven.waltid.dev/snapshots")
                url = uri(
                    if (version.toString()
                            .endsWith("SNAPSHOT")
                    ) snapshotsRepoUrl else releasesRepoUrl
                )
                credentials {
                    username = secretMavenUsername
                    password = secretMavenPassword
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
