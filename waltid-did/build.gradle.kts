plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt.did"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}


kotlin {
    jvmToolchain(15)
}

kotlin {
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
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("io.github.optimumcode:json-schema-validator:0.0.2")

                // Ktor client
                implementation("io.ktor:ktor-client-core:2.3.4")
                implementation("io.ktor:ktor-client-serialization:2.3.4")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.4")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.4")
                implementation("io.ktor:ktor-client-json:2.3.4")
                implementation("io.ktor:ktor-client-logging:2.3.4")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

                // Date
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

                // Crypto
                api(project(":waltid-crypto"))

                // Logging
                implementation("io.github.oshai:kotlin-logging:5.1.0")

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Ktor client
                implementation("io.ktor:ktor-client-cio:2.3.4")

                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.9")

                // Json canonicalization
                implementation("io.github.erdtman:java-json-canonicalization:1.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-params:5.9.0")
                implementation("io.ktor:ktor-server-test-host:2.3.5")
                implementation("io.ktor:ktor-server-content-negotiation:2.3.5")
                implementation("io.ktor:ktor-server-netty:2.3.5")
            }
        }
        publishing {
            repositories {
                maven {
                    url = uri("https://maven.walt.id/repository/waltid/")
                    val envUsername = null //java.lang.System.getenv("MAVEN_USERNAME")
                    val envPassword = null //java.lang.System.getenv("MAVEN_PASSWORD")

                    val usernameFile = File("secret_maven_username.txt")
                    val passwordFile = File("secret_maven_password.txt")

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
        }
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}
