import love.forte.plugin.suspendtrans.ClassInfo
import love.forte.plugin.suspendtrans.SuspendTransformConfiguration
import love.forte.plugin.suspendtrans.TargetPlatform
import love.forte.plugin.suspendtrans.gradle.SuspendTransformGradleExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
//    id("com.android.library")
    id("love.forte.plugin.suspend-transform") version "2.0.20-Beta1-0.9.2"
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

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

kotlin {
    jvmToolchain(15)
}

/*android {
    namespace = "id.walt.crypto"
    compileSdk = 34
}*/

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_15 // JVM got Ed25519 at version 15
                }
            }
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

//    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

                // Crypto
                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.5.1"))
                implementation("org.kotlincrypto.hash:sha2")

                // Logging
                implementation("io.github.oshai:kotlin-logging:7.0.0")

                // walt.id
                api(project(":waltid-libraries:waltid-crypto"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")

                // OCI
                implementation("com.oracle.oci.sdk:oci-java-sdk-shaded-full:3.44.3")

                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:9.40")

                // walt.id
                api(project(":waltid-libraries:waltid-crypto"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.13")

                // Test
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.11.0-M2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.0-M2")
            }
        }
//        val androidMain by getting {
//            dependencies {
//                implementation("io.ktor:ktor-client-android:2.3.10")
//            }
//        }
//        val androidUnitTest by getting {
//            dependencies {
//                implementation(kotlin("test"))
//            }
//        }
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
