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

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("io.github.optimumcode:json-schema-validator:0.2.3")

                implementation(project(":waltid-libraries:credentials:waltid-verifiable-credentials"))

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
    }
}
