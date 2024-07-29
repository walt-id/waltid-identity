import org.jetbrains.kotlin.gradle.dsl.JvmTarget

object Versions {
    const val KTOR_VERSION = "2.3.12"
}

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
    id("io.ktor.plugin") version "2.3.12" // Versions.KTOR_VERSION
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

group = "id.walt.cli"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

kotlin {
    jvmToolchain(17)
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_15 // JVM got Ed25519 at version 15
                }
            }
        }
        withJava()
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":waltid-libraries:waltid-crypto"))
                api(project(":waltid-libraries:waltid-did"))
                api(project(":waltid-libraries:waltid-verifiable-credentials"))
                api(project(":waltid-libraries:waltid-sdjwt"))
                api(project(":waltid-libraries:waltid-openid4vc"))

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("com.google.code.gson:gson:2.11.0")

                // CLI
                implementation("com.github.ajalt.mordant:mordant:2.7.1")
                implementation("com.github.ajalt.clikt:clikt:4.4.0")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

                // Logging
                implementation("io.github.oshai:kotlin-logging:7.0.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.13")

                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:9.40")

                // BouncyCastle for PEM import
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.6")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("com.wolpl.clikt-testkit:clikt-testkit:2.0.0")

                implementation("org.junit.jupiter:junit-jupiter-params:5.11.0-M2")

                // Ktor server
                implementation("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-netty-jvm:${Versions.KTOR_VERSION}")
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

                // Ktor client
                implementation("io.ktor:ktor-client-core-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-serialization-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-json-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")

            }
        }
        /*publishing {
            repositories {
                maven {
                    url = uri("https://maven.waltid.dev/releases")
                    val envUsername = System.getenv("MAVEN_USERNAME")
                    val envPassword = System.getenv("MAVEN_PASSWORD")

                    val usernameFile = File("$rootDir/secret_maven_username.txt")
                    val passwordFile = File("$rootDir/secret_maven_password.txt")

                    val secretMavenUsername = envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" }
                    //println("Deploy username length: ${secretMavenUsername.length}")
                    val secretMavenPassword = envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }

                    //if (secretMavenPassword.isBlank()) {
                    //   println("WARNING: Password is blank!")
                    //}

                    credentials {
                        username = secretMavenUsername
                        password = secretMavenPassword
                    }
                }
            }
        }*/
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}

application {
    // Define the main class for the application.
    // Works with:
    //     ../gradlew run --args="--help"
    mainClass = "id.walt.cli.MainKt"
    applicationName = "waltid"
}

tasks.test {
    useJUnitPlatform()
}
