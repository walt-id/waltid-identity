object Versions {
    const val KOTLIN_VERSION = "1.9.22" // also change 2 plugins
    const val KTOR_VERSION = "2.3.8" // also change 1 plugin
    const val COROUTINES_VERSION = "1.8.0"
    const val EXPOSED_VERSION = "0.43.0"
    const val HOPLITE_VERSION = "2.8.0.RC3"
}

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
    id("io.ktor.plugin") version "2.3.8"
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
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "15" // JVM got Ed25519 at version 15
        }
        withJava()
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":waltid-crypto"))
                api(project(":waltid-did"))
                api(project(":waltid-verifiable-credentials"))
                api(project(":waltid-sdjwt"))
                api(project(":waltid-openid4vc"))

                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.3.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0-RC.2")
                implementation ("com.google.code.gson:gson:2.10.1")

                // CLI
                implementation("com.varabyte.kotter:kotter-jvm:1.1.2")
                implementation("com.github.ajalt.mordant:mordant:2.6.0")
                implementation("com.github.ajalt.clikt:clikt:4.2.2")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

                // Logging
                implementation("io.github.oshai:kotlin-logging:6.0.4")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.13")

                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

                // BouncyCastle for PEM import
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.6")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("com.wolpl.clikt-testkit:clikt-testkit:2.0.0")

                implementation("org.junit.jupiter:junit-jupiter-params:5.10.2")

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
