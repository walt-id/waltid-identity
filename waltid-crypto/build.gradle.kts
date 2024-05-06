import love.forte.plugin.suspendtrans.ClassInfo
import love.forte.plugin.suspendtrans.SuspendTransformConfiguration
import love.forte.plugin.suspendtrans.TargetPlatform
import love.forte.plugin.suspendtrans.gradle.SuspendTransformGradleExtension

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
    id("love.forte.plugin.suspend-transform") version "0.6.0"
}

group = "id.walt.crypto"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

suspendTransform {
    enabled = true
    includeRuntime = true
    useDefault()
}

tasks.withType<org.gradle.language.jvm.tasks.ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

kotlin {
    jvmToolchain(15)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

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
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
    js(IR) {
        moduleName = "crypto"
        /*browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }*/
        nodejs {
            generateTypeScriptDefinitions()
            testTask {
                useMocha()
            }
        }
        binaries.library()
    }

    sourceSets {
        val androidMain by getting {
            dependencies { /* Add dependencies here */ }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                implementation("androidx.test.ext:junit:1.1.5")
                implementation("androidx.test:runner:1.5.2")
                implementation("androidx.test:rules:1.5.0")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
                implementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
            }
        }
        val commonMain by getting {
            dependencies {
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

                // Ktor client
                implementation("io.ktor:ktor-client-core:2.3.10")
                implementation("io.ktor:ktor-client-serialization:2.3.8")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.10")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.10")
                implementation("io.ktor:ktor-client-json:2.3.10")
                implementation("io.ktor:ktor-client-logging:2.3.10")

                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.5.1"))
                implementation("org.kotlincrypto.hash:sha2")

                // Date
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

                // Logging
                implementation("io.github.oshai:kotlin-logging:6.0.4")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                //implementation("dev.whyoleg.cryptography:cryptography-jdk:0.1.0")
                implementation("com.google.crypto.tink:tink:1.12.0") // for JOSE using Ed25519

                implementation("org.bouncycastle:bcprov-lts8on:2.73.6") // for secp256k1 (which was removed with Java 17)
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.4") // PEM import

                // Ktor client
                implementation("io.ktor:ktor-client-cio:2.3.10")

                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.13")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.0")

                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

                // Multibase
//                implementation("com.github.multiformats:java-multibase:v1.1.1")

                implementation("com.oracle.oci.sdk:oci-java-sdk-shaded-full:3.41.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                // Logging
//                implementation("org.slf4j:slf4j-simple:2.0.13")

                // Test
                implementation(kotlin("test"))

                implementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
            }
        }
        val jsMain by getting {
            dependencies {
                // JOSE
                implementation(npm("jose", "5.2.3"))

                // Multibase
                // implementation(npm("multiformats", "12.1.2"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        publishing {
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
        }
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}

extensions.getByType<SuspendTransformGradleExtension>().apply {
    transformers[TargetPlatform.JS] = mutableListOf(
        SuspendTransformConfiguration.jsPromiseTransformer.copy(
            copyAnnotationExcludes = listOf(
                ClassInfo("kotlin.js", "JsExport.Ignore")
            )
        )
    )
}

android {
    namespace = "id.walt.crypto"
    compileSdk = 34
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}