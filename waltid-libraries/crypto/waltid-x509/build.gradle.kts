@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

plugins {
    kotlin("multiplatform")
    kotlin("plugin.power-assert")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.5.2"
    id("com.github.ben-manes.versions")
}

group = "id.walt.credentials"

repositories {
    mavenCentral()
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
    js(IR) {
        outputModuleName = "x509"
        useEsModules()
        nodejs {
            generateTypeScriptDefinitions()
            testTask {
                useMocha()
            }
        }
        binaries.library()
    }
    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    //val ktor_version = "3.2.0"

    sourceSets {

        all {
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")

        }
        val commonMain by getting {
            dependencies {
                // CBOR
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

                // Waltid
                /*
implementation(project(":waltid-libraries:crypto:waltid-crypto"))
implementation(project(":waltid-libraries:crypto:waltid-cose"))



// Ktor client
implementation("io.ktor:ktor-client-core:$ktor_version")
implementation("io.ktor:ktor-client-serialization:$ktor_version")
implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
implementation("io.ktor:ktor-client-json:$ktor_version")
implementation("io.ktor:ktor-client-logging:$ktor_version")

implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
implementation("org.kotlincrypto.hash:sha2")

// Date
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

// Cache
implementation("io.github.reactivecircus.cache4k:cache4k:0.14.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

// Logging
implementation("io.github.oshai:kotlin-logging:7.0.5")

implementation("${SuspendTransPluginConstants.ANNOTATION_GROUP}:${SuspendTransPluginConstants.ANNOTATION_NAME}:${SuspendTransPluginConstants.ANNOTATION_VERSION}")


implementation(project.dependencies.platform("org.kotlincrypto.macs:bom:0.6.1"))
implementation("org.kotlincrypto.macs:hmac-sha2")*/

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
        val jvmMain by getting {
            dependencies {

            }
        }
        val jvmTest by getting {
            dependencies {
                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.16")

                // Test
                implementation(kotlin("test"))

                implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")

                // Bouncy Castle
                implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
            }
        }
        val jsMain by getting {
            dependencies {

            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        if (enableIosBuild) {
            val iosArm64Main by getting
            val iosSimulatorArm64Main by getting

            val iosMain by creating {
                dependsOn(commonMain)
                iosArm64Main.dependsOn(this)
                iosSimulatorArm64Main.dependsOn(this)
                dependencies {
                    implementation(project(":waltid-libraries:crypto:waltid-target-ios"))
                }
            }

            val iosArm64Test by getting
            val iosSimulatorArm64Test by getting

            val iosTest by creating {
                dependsOn(commonTest)
                iosArm64Test.dependsOn(this)
                iosSimulatorArm64Test.dependsOn(this)
            }
        }
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

powerAssert {
    includedSourceSets = listOf("commonTest")
    functions = listOf(
        // kotlin.test
        "kotlin.assert", "kotlin.test.assertEquals", "kotlin.test.assertNull", "kotlin.test.assertTrue", "kotlin.test.assertFalse",
        "kotlin.test.assertContentEquals",

        // checks
        "kotlin.require", "kotlin.check"
    )
}

npmPublish {
    registries {
        val envToken = System.getenv("NPM_TOKEN")
        val npmTokenFile = File("secret_npm_token.txt")
        val secretNpmToken = envToken ?: npmTokenFile.let { if (it.isFile) it.readLines().first() else "" }
        val hasNPMToken = secretNpmToken.isNotEmpty()
        val isReleaseBuild = Regex("\\d+.\\d+.\\d+").matches(version.get())
        if (isReleaseBuild && hasNPMToken) {
            readme.set(File("README.md"))
            register("npmjs") {
                uri.set(uri("https://registry.npmjs.org"))
                authToken.set(secretNpmToken)
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id X.509")
                description.set("walt.id Kotlin/Java library X.509")
                url.set("https://walt.id")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("walt.id")
                        name.set("walt.id")
                        email.set("office@walt.id")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(if (version.toString().endsWith("SNAPSHOT")) uri("https://maven.waltid.dev/snapshots") else uri("https://maven.waltid.dev/releases"))
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: File("$rootDir/secret_maven_username.txt").let { if (it.isFile) it.readLines().first() else "" }
                password = System.getenv("MAVEN_PASSWORD") ?: File("$rootDir/secret_maven_password.txt").let { if (it.isFile) it.readLines().first() else "" }
            }
        }
    }
}
