import love.forte.plugin.suspendtrans.ClassInfo
import love.forte.plugin.suspendtrans.SuspendTransformConfiguration
import love.forte.plugin.suspendtrans.TargetPlatform
import love.forte.plugin.suspendtrans.gradle.SuspendTransformGradleExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
//    id("com.android.library")
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

/*android {
    namespace = "id.walt.crypto"
    compileSdk = 34
}*/

kotlin {
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

//    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

                // Crypto
                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.5.1"))
                implementation("org.kotlincrypto.hash:sha2")

                // Logging
                implementation("io.github.oshai:kotlin-logging:6.0.9")

                // walt.id
                api(project(":waltid-crypto"))
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
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.0")

                // OCI
                implementation("com.oracle.oci.sdk:oci-java-sdk-shaded-full:3.41.0")

                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

                // walt.id
                api(project(":waltid-crypto"))
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
