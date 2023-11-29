plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
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
                api(project(":waltid-crypto"))
                api(project(":waltid-did"))
                api(project(":waltid-verifiable-credentials"))
                api(project(":waltid-sdjwt"))
                api(project(":waltid-openid4vc"))

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

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
                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.9")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.junit.jupiter:junit-jupiter-params:5.9.0")
            }
        }
        /*publishing {
            repositories {
                maven {
                    url = uri("https://maven.walt.id/repository/waltid/")
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
