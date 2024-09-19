import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.4.3"
    id("love.forte.plugin.suspend-transform") version "2.0.20-0.9.2"
    id("com.github.ben-manes.versions")
}

group = "id.walt.policies"

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
        moduleName = "verification-policies"
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }
    val ktor_version = "2.3.12"
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("io.github.optimumcode:json-schema-validator:0.2.2")

                implementation(project(":waltid-libraries:credentials:waltid-verifiable-credentials"))

                // Kotlinx
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

                // Ktor client
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-serialization:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("io.ktor:ktor-client-json:$ktor_version")
                implementation("io.ktor:ktor-client-logging:$ktor_version")

                // Loggin
                implementation("io.github.oshai:kotlin-logging:7.0.0")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
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
