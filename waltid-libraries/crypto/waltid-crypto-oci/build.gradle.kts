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
    id("love.forte.plugin.suspend-transform")
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
    jvmToolchain(17)
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

                // Crypto
                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
                implementation("org.kotlincrypto.hash:sha2")

                // Logging
                implementation("io.github.oshai:kotlin-logging:7.0.5")

                // walt.id
                api(project(":waltid-libraries:crypto:waltid-crypto"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.1")

                // OCI
                implementation("com.oracle.oci.sdk:oci-java-sdk-shaded-full:3.57.1")

                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")

                // walt.id
                api(project(":waltid-libraries:crypto:waltid-crypto"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.16")

                // Test
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
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
