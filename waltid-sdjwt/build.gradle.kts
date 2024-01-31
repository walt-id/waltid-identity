plugins {
    kotlin("multiplatform")

    id("org.jetbrains.kotlin.plugin.serialization")

    id("dev.petuska.npm.publish") version "3.4.1"
    `maven-publish`
    id("com.github.ben-manes.versions")
}

group = "id.walt"

repositories {
    mavenCentral()
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    jvm {
        jvmToolchain(15) // 16 possible?
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js(IR) {
        browser {
            commonWebpackConfig(Action {
                cssSupport {
                    enabled.set(true)
                }
            })

            testTask(Action {
                useKarma {
                    useChromiumHeadless()
                }
            })
        }
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }

    val kryptoVersion = "4.0.10"


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("dev.whyoleg.cryptography:cryptography-random:0.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("com.soywiz.korlibs.krypto:krypto:$kryptoVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:5.7.2")

                implementation("io.kotest:kotest-assertions-json:5.7.2")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.nimbusds:nimbus-jose-jwt:9.37.1")
            }
        }
        val jvmTest by getting {
            dependencies {
//              implementation("io.mockk:mockk:1.13.2")

                implementation("io.kotest:kotest-runner-junit5:5.7.2")
                implementation("io.kotest:kotest-assertions-core:5.7.2")
                implementation("io.kotest:kotest-assertions-json:5.7.2")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jose", "~4.14.4"))
            }
        }
        val jsTest by getting {

        }
    }

    publishing {
        repositories {
            maven {
                url = uri("https://maven.walt.id/repository/waltid/")
                val envUsername = System.getenv("MAVEN_USERNAME")
                val envPassword = System.getenv("MAVEN_PASSWORD")

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
}

npmPublish {
    registries {
        val envToken = System.getenv("NPM_TOKEN")
        val npmTokenFile = File("secret_npm_token.txt")
        val secretNpmToken = envToken ?: npmTokenFile.let { if (it.isFile) it.readLines().first() else "" }
        val hasNPMToken = secretNpmToken.isNotEmpty()
        val isReleaseBuild = Regex("\\d+.\\d+.\\d+").matches(version.get())
        if (isReleaseBuild && hasNPMToken) {
            readme.set(File("NPM_README.md"))
            register("npmjs") {
                uri.set(uri("https://registry.npmjs.org"))
                authToken.set(secretNpmToken)
            }
        }
    }
}
