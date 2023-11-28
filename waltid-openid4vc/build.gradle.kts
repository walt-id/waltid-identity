plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("dev.petuska.npm.publish") version "3.4.1"
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt"
version = "1.SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.multiformats")
        }
    }
    maven("https://maven.walt.id/repository/waltid-ssi-kit/") {
        content {
            includeGroup("id.walt")
        }
    }
    maven("https://repo.danubetech.com/repository/maven-public/")
    maven("https://maven.walt.id/repository/waltid/") {
        content {
            includeGroup("id.walt")
            includeGroup("id.walt.servicematrix")
            //includeGroup("info.weboftrust")
            includeGroup("decentralized-identity")
            //includeGroup("com.danubetech")
        }
    }
    mavenLocal()
}

kotlin {
    jvm {
        jvmToolchain(17)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        nodejs() {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }
//    val hostOs = System.getProperty("os.name")
//    val isMingwX64 = hostOs.startsWith("Windows")
//    val nativeTarget = when {
//        hostOs == "Mac OS X" -> macosX64("native")
//        hostOs == "Linux" -> linuxX64("native")
//        isMingwX64 -> mingwX64("native")
//        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
//    }
    val ktor_version = "2.3.6"


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("io.ktor:ktor-http:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                // TODO: set to project version: implementation(project(":waltid-sdjwt"))
                implementation("id.walt:waltid-sd-jwt:1.2310101347.0")
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
                implementation("io.ktor:ktor-client-java:$ktor_version")
            }
        }
        val jvmTest by getting {
            dependencies {
                //implementation("io.mockk:mockk:1.13.2")

                implementation("io.kotest:kotest-runner-junit5:5.7.2")
                implementation("io.kotest:kotest-assertions-core:5.7.2")
                implementation("io.kotest:kotest-assertions-json:5.7.2")

                implementation("id.walt.servicematrix:WaltID-ServiceMatrix:1.1.3")
                // TODO: current version implementation("id.walt:waltid-ssikit:1.2311131043.0")
                implementation("id.walt:waltid-ssikit:1.JWTTYP")
                implementation(project(":waltid-crypto"))

                implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                //implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-java:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")

            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jose", "~4.14.4"))
            }
        }
        val jsTest by getting {

        }
//        val nativeMain by getting
//        val nativeTest by getting
    }

    publishing {
        repositories {
            val envUsername = System.getenv("MAVEN_USERNAME")
            val envPassword = System.getenv("MAVEN_PASSWORD")
            val usernameFile = File("secret_maven_username.txt")
            val passwordFile = File("secret_maven_password.txt")
            val secretMavenUsername = envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" }
            val secretMavenPassword = envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }
            val hasMavenAuth = secretMavenUsername.isNotEmpty() && secretMavenPassword.isNotEmpty()
            if (hasMavenAuth) {
                maven {
                    url = uri("https://maven.walt.id/repository/waltid-ssi-kit/")
                    credentials {
                        username = secretMavenUsername
                        password = secretMavenPassword
                    }
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
            println("-- RELEASE BUILD & NPM TOKEN --")
            readme.set(File("README.md"))
            register("npmjs") {
                uri.set(uri("https://registry.npmjs.org"))
                authToken.set(secretNpmToken)
            }
        }
    }
}
