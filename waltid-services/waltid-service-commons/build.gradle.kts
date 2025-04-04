plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")

    id("com.github.ben-manes.versions")
}

group = "id.walt"

repositories {
    mavenLocal()
    mavenCentral()
}

object Versions {
    const val KTOR_VERSION = "3.1.1" // also change 1 plugin
}

dependencies {
    api(project(":waltid-libraries:waltid-library-commons"))
    // OIDC
    api(project(":waltid-libraries:protocols:waltid-openid4vc"))

    // Ktor
    api("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
    api("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")

    // Logging
    api("io.klogging:klogging-jvm:0.9.1") // JVM + ~JS
    implementation("io.klogging:slf4j-klogging:0.9.1")
    implementation("org.slf4j:jul-to-slf4j:2.0.16")

    // CLI
    api("com.github.ajalt.clikt:clikt:5.0.3")  // JVM

    // Config
    api("com.sksamuel.hoplite:hoplite-core:2.9.0")
    api("com.sksamuel.hoplite:hoplite-hocon:2.9.0")
    api("com.sksamuel.hoplite:hoplite-hikaricp:2.9.0")

    // Kotlinx.serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Health checks
    api("com.sksamuel.cohort:cohort-ktor:2.6.1")

    // OpenAPI
    api("io.github.smiley4:ktor-swagger-ui:4.1.6")
    implementation("io.github.smiley4:schema-kenerator-core:1.6.5")
    implementation("io.github.smiley4:schema-kenerator-serialization:1.6.5")
    implementation("io.github.smiley4:schema-kenerator-reflection:1.6.5")
    implementation("io.github.smiley4:schema-kenerator-swagger:1.6.5")

    // Persistence
    api("io.github.reactivecircus.cache4k:cache4k:0.14.0")
    api("app.softwork:kotlinx-uuid-core:0.1.4")
    api("redis.clients:jedis:5.2.0")

    // Testing
    testApi(kotlin("test"))
    testApi("io.ktor:ktor-server-test-host:${Versions.KTOR_VERSION}")
    testApi("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
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
