import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

object Versions {
    const val KTOR_VERSION = "3.2.2" // also change 1 plugin
    const val COROUTINES_VERSION = "1.10.1"
    const val HOPLITE_VERSION = "2.9.0"
}

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

    id("io.ktor.plugin") version "3.2.2" // Versions.KTOR_VERSION
    id("org.owasp.dependencycheck") version "9.2.0"
    id("com.github.jk1.dependency-license-report") version "2.9"
    id("maven-publish")
    id("com.github.ben-manes.versions")
    application
}

group = "id.walt"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.waltid.dev/releases")
    maven("https://maven.waltid.dev/snapshots")
}


dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-sessions-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auto-head-response-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-double-receive-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-host-common-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-compression-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-cors-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-call-logging-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-call-id-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-sse-jvm:${Versions.KTOR_VERSION}")

    // Ktor client
    implementation("io.ktor:ktor-client-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-json-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")

    // Uuid
    implementation("app.softwork:kotlinx-uuid-core:0.1.6")

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.5")
    implementation("org.slf4j:jul-to-slf4j:2.0.16")
    implementation("io.klogging:klogging-jvm:0.11.6")
    implementation("io.klogging:slf4j-klogging:0.11.6")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES_VERSION}")
    testImplementation(project(":waltid-services:waltid-service-commons-test"))
    testApi(project(":waltid-libraries:protocols:waltid-openid4vp-wallet"))
    testImplementation(project(":waltid-libraries:credentials:waltid-holder-policies"))

    api(project(":waltid-libraries:credentials:waltid-dcql"))
    api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
    api(project(":waltid-libraries:credentials:waltid-verification-policies2"))
    api(project(":waltid-libraries:credentials:waltid-vical"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp-verifier"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp-verifier-openapi"))
    implementation(project(":waltid-libraries:web:waltid-ktor-notifications"))
}
tasks.withType<Zip> {
    isZip64 = true
}
tasks.withType<Test> {
    useJUnitPlatform()

    // Use the following condition to optionally run the integration tests:
    // > gradle build -PrunIntegrationTests
    if (!project.hasProperty("runIntegrationTests")) {
        exclude("id/walt/test/integration/**")
    }
}

tasks.withType<ProcessResources> {
    doLast {
        layout.buildDirectory.get().file("resources/main/version.properties").asFile.run {
            parentFile.mkdirs()
            Properties().run {
                setProperty("version", rootProject.version.toString())
                writer().use { store(it, "walt.id version store") }
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(
            windowsScript.readText().replace(Regex("set CLASSPATH=.*"), "set CLASSPATH=%APP_HOME%\\\\lib\\\\*")
        )
    }
}

application {
    mainClass.set("id.walt.openid4vp.verifier.MainKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// Define publication to allow publishing to local maven repo with the command:  ./gradlew publishToMavenLocal
// This should not be published to https://maven.waltid.dev/ to save storage
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id Verifier API REST service")
                description.set(
                    """
                    Kotlin/Java REST service for verifying digital credentials
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
}

fun waltidPrivateCredentials(repoName:String): Pair<String, String> = let {
    val envUsername = System.getenv(repoName.uppercase() + "_USERNAME")
    val envPassword = System.getenv(repoName.uppercase() + "_PASSWORD")

    val usernameFile = File("$rootDir/secret-${repoName.lowercase()}-username.txt")
    val passwordFile = File("$rootDir/secret-${repoName.lowercase()}-password.txt")

    return Pair(
        envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" },
        envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }
    )
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("waltid/verifier-api2")
        imageTag.set("${project.version}")
        portMappings.set(listOf(
            io.ktor.plugin.features.DockerPortMapping(
                7003,
                7003,
                io.ktor.plugin.features.DockerPortMappingProtocol.TCP
            )
        ))

        val (username, password) = waltidPrivateCredentials("DOCKER")
        externalRegistry.set(
            io.ktor.plugin.features.DockerImageRegistry.dockerHub(
                appName = provider { "verifier-api" },
                username = provider { username },
                password = provider { password }
            )
        )
    }

    jib {
        container {
            mainClass = "id.walt.openid4vp.verifier.MainKt"
            workingDirectory = "/waltid-verifier-api2"
        }
        from {
            platforms {
                platform {
                    architecture = "amd64"
                    os = "linux"
                }
                platform {
                    architecture = "arm64"
                    os = "linux"
                }
            }
        }
    }
}
