import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.4.3"
    // id("love.forte.plugin.suspend-transform") version "2.0.20-Beta1-0.9.2"
    id("com.github.ben-manes.versions")
}

group = "id.walt.dif-presentation-exchange"

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
        moduleName = "presentation-exchange"
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("io.github.optimumcode:json-schema-validator:0.2.2")

                implementation(project(":waltid-libraries:credentials:waltid-verifiable-credentials"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.slf4j:slf4j-simple:2.0.13")
            }
        }
    }
}
