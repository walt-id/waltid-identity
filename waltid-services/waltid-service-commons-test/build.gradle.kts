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
    const val KTOR_VERSION = "3.2.2" // also change 1 plugin
}

dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    // Testing
    api(kotlin("test"))
    api("io.ktor:ktor-server-test-host:${Versions.KTOR_VERSION}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

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
val testJar by tasks.register<Jar>(Jar::class.toString()) {
    archiveClassifier.set("test")
    from(sourceSets["test"].output)
}

artifacts {
    add("testArtifacts", testJar)
}

publishing {
    publications {
        // Main sources
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id service-commons-test")
                description.set(
                    """
                    Kotlin/Java library for walt.id services-commons-test
                    """.trimIndent()
                )
                url.set("https://walt.id")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("walt.id")
                        name.set("walt.id")
                        email.set("office@walt.id")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(if (version.toString().endsWith("SNAPSHOT")) uri("https://maven.waltid.dev/snapshots") else uri("https://maven.waltid.dev/releases"))
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: File("$rootDir/secret_maven_username.txt").let { if (it.isFile) it.readLines().first() else "" }
                password = System.getenv("MAVEN_PASSWORD") ?: File("$rootDir/secret_maven_password.txt").let { if (it.isFile) it.readLines().first() else "" }
            }
        }
    }
}
