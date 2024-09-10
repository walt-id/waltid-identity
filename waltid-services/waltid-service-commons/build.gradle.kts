plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
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

    // CLI
    api("com.github.ajalt.clikt:clikt:4.4.0")  // JVM

    // Config
    api("com.sksamuel.hoplite:hoplite-core:2.8.0.RC3")
    api("com.sksamuel.hoplite:hoplite-hocon:2.8.0.RC3")
    api("com.sksamuel.hoplite:hoplite-hikaricp:2.8.0.RC3")

    // Kotlinx.serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    // Health checks
    api("com.sksamuel.cohort:cohort-ktor:2.5.1")

    // OpenAPI
    api("io.github.smiley4:ktor-swagger-ui:3.3.0")
    implementation("io.github.smiley4:schema-kenerator-core:1.1.0")
    implementation("io.github.smiley4:schema-kenerator-serialization:1.0.1")
    implementation("io.github.smiley4:schema-kenerator-reflection:1.1.0")
    implementation("io.github.smiley4:schema-kenerator-swagger:1.1.0")

    // Persistence
    api("io.github.reactivecircus.cache4k:cache4k:0.13.0")
    api("app.softwork:kotlinx-uuid-core:0.1.2")
    api("redis.clients:jedis:5.2.0-beta4")

    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
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
