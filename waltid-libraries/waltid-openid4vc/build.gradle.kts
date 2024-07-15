import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.petuska.npm.publish") version "3.4.3"
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt.openid4vc"

repositories {
    mavenCentral()
    maven("https://maven.waltid.dev/releases") {
        content {
            includeGroup("id.walt")
        }
    }
    mavenLocal()
}

val targetVersion = JavaVersion.VERSION_1_8
val toolingRuntime = JavaVersion.VERSION_21

java {
    sourceCompatibility = targetVersion
    targetCompatibility = targetVersion
}

tasks.withType(KotlinCompile::class.java) {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(targetVersion.toString()))
    }
}

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

    jvmToolchain(toolingRuntime.majorVersion.toInt())
    jvm {
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
        nodejs {
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
    val ktor_version = "2.3.12"

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

                // HTTP
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

                implementation("io.github.oshai:kotlin-logging:7.0.0")

                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

                // walt.id
                implementation(project(":waltid-libraries:waltid-crypto"))
                implementation(project(":waltid-libraries:waltid-mdoc-credentials"))
                implementation(project(":waltid-libraries:waltid-did"))
                implementation(project(":waltid-libraries:waltid-sdjwt"))

                // -- Multiplatform --
                // Multiplatform / UUID
                implementation("app.softwork:kotlinx-uuid-core:0.0.26")

                // Multiplatform / Date & time
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

                // Multiplatform / Hashes
                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.5.1"))
                implementation("org.kotlincrypto.hash:sha2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":waltid-libraries:waltid-crypto"))
                implementation(project(":waltid-libraries:waltid-did"))
                implementation(project(":waltid-libraries:waltid-verifiable-credentials"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.nfeld.jsonpathkt:jsonpathkt:2.0.1")
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("com.nimbusds:nimbus-jose-jwt:9.40")
                implementation("io.kotest:kotest-runner-junit5:5.9.1")
                implementation("io.kotest:kotest-assertions-core:5.9.1")
                implementation("io.kotest:kotest-assertions-json:5.9.1")
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.0-M2")
                implementation("com.google.crypto.tink:tink:1.14.0") // for JOSE using Ed25519
                implementation("org.bouncycastle:bcprov-lts8on:2.73.6") // for secp256k1 (which was removed with Java 17)
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.6") // PEM import

                implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-auth:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.8.1")
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")

                implementation("com.augustcellars.cose:cose-java:1.1.0")
                implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")

            }
        }
        val jsMain by getting {
            dependencies {
                // implementation(npm("jose", "~4.14.4"))
                implementation(npm("jose", "5.2.3"))
                implementation("io.ktor:ktor-client-js:$ktor_version")
            }
        }
        val jsTest by getting {

        }
//        val nativeMain by getting
//        val nativeTest by getting
        // Add for native: implementation("io.ktor:ktor-client-cio:$ktor_version")
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
                    val releasesRepoUrl = uri("https://maven.waltid.dev/releases")
                    val snapshotsRepoUrl = uri("https://maven.waltid.dev/snapshots")
                    url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
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
