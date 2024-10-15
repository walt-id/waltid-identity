import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.4.3"
    // id("love.forte.plugin.suspend-transform") version "2.0.20-0.9.2"
    id("com.github.ben-manes.versions")
}

group = "id.walt.dif-definitions-parser"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
    val enableAndroidBuild = getSetting("enableAndroidBuild")
    val enableIosBuild = getSetting("enableIosBuild")

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        withJava()
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

    js(IR) {
        moduleName = "definitions-parser"
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("io.github.optimumcode:json-schema-validator:0.2.3")

                implementation(project(":waltid-libraries:credentials:waltid-verifiable-credentials"))

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

                // Loggin
                implementation("io.github.oshai:kotlin-logging:7.0.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.slf4j:slf4j-simple:2.0.16")
            }
        }

        if (enableIosBuild) {
            val iosArm64Main by getting
            val iosSimulatorArm64Main by getting

            val iosMain by creating {
                dependsOn(commonMain)
                iosArm64Main.dependsOn(this)
                iosSimulatorArm64Main.dependsOn(this)
            }

            val iosArm64Test by getting
            val iosSimulatorArm64Test by getting

            val iosTest by creating {
                dependsOn(commonTest)
                iosArm64Test.dependsOn(this)
                iosSimulatorArm64Test.dependsOn(this)
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                name.set("walt.id DIF Definitions Parser")
                description.set(
                    """
                    Kotlin/Java library for DIF definitions parsing
                    """.trimIndent()
                )
                url.set("https://walt.id")
            }
            from(components["kotlin"])
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
