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
    const val KTOR_VERSION = "3.1.2" // also change 1 plugin
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
    api("io.klogging:klogging-jvm:0.9.4") // JVM + ~JS
    implementation("io.klogging:slf4j-klogging:0.9.4")
    implementation("org.slf4j:jul-to-slf4j:2.0.17")

    // CLI
    api("com.github.ajalt.clikt:clikt:5.0.3")  // JVM

    // Config
    api("com.sksamuel.hoplite:hoplite-core:2.9.0")
    api("com.sksamuel.hoplite:hoplite-hocon:2.9.0")
    api("com.sksamuel.hoplite:hoplite-hikaricp:2.9.0")

    // Kotlinx.serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Health checks
    api("com.sksamuel.cohort:cohort-ktor:2.6.2")

    // OpenAPI
    api("io.github.smiley4:ktor-openapi:5.0.2")
    implementation("io.github.smiley4:ktor-swagger-ui:5.0.2")
    implementation("io.github.smiley4:ktor-redoc:5.0.2")

    implementation("io.github.smiley4:schema-kenerator-core:2.2.0")
    implementation("io.github.smiley4:schema-kenerator-swagger:2.2.0")
    implementation("io.github.smiley4:schema-kenerator-serialization:2.2.0")
    implementation("io.github.smiley4:schema-kenerator-reflection:2.2.0")

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
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id service-commons")
                description.set(
                    """
                    Kotlin/Java library for the walt.id services-commons
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
