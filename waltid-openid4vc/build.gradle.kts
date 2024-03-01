import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.petuska.npm.publish") version "3.4.2"
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt"

repositories {
    mavenCentral()
    /*maven("https://jitpack.io") {
        content {
            includeGroup("com.github.multiformats")
        }
    }*/
    maven("https://repo.danubetech.com/repository/maven-public/")
    maven("https://maven.walt.id/repository/waltid/") {
        content {
            includeGroup("id.walt")
            includeGroup("id.walt.servicematrix")
            //includeGroup("info.weboftrust")
            includeGroup("decentralized-identity")
            //includeGroup("com.danubetech")
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
    jvm {
        jvmToolchain(toolingRuntime.majorVersion.toInt())
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
    val ktor_version = "2.3.8"
    val HOPLITE_VERSION = "2.8.0.RC3"

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("io.ktor:ktor-http:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation(project(":waltid-sdjwt"))
                implementation("app.softwork:kotlinx-uuid-core:0.0.22")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":waltid-crypto"))
                implementation(project(":waltid-did"))
                implementation(project(":waltid-verifiable-credentials"))
                implementation("io.kotest:kotest-assertions-core:5.8.0")

                implementation("io.kotest:kotest-assertions-json:5.8.0")

                implementation("io.github.microutils:kotlin-logging:1.12.5")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:$ktor_version")
                implementation("org.sqids:sqids:0.1.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                //implementation("io.mockk:mockk:1.13.2")
                implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
                implementation("io.kotest:kotest-runner-junit5:5.8.0")
                implementation("io.kotest:kotest-assertions-core:5.8.0")
                implementation("io.kotest:kotest-assertions-json:5.8.0")
                implementation("com.google.crypto.tink:tink:1.12.0") // for JOSE using Ed25519
                // Multibase
                // implementation("com.github.multiformats:java-multibase:v1.1.1")
                // TODO: current version implementation("id.walt:waltid-ssikit:1.2311131043.0")
                //implementation("id.walt:waltid-ssikit:1.JWTTYP") {
                //    exclude("waltid-sd-jwt-jvm")
                //    exclude(module = "waltid-sd-jwt-jvm")
                //}
                implementation("org.bouncycastle:bcprov-lts8on:2.73.4") // for secp256k1 (which was removed with Java 17)
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.4") // PEM import
                implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")

                implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-java:$ktor_version")
                implementation("io.ktor:ktor-client-auth:$ktor_version")
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")
                implementation("com.sksamuel.hoplite:hoplite-core:2.7.5")
                implementation("com.sksamuel.hoplite:hoplite-yaml:2.7.5")
                implementation("com.sksamuel.hoplite:hoplite-hikaricp:2.7.5")
                implementation("org.yaml:snakeyaml:2.2")
                implementation("org.xerial:sqlite-jdbc:3.44.1.0")

                // Config
                implementation("com.sksamuel.hoplite:hoplite-core:${HOPLITE_VERSION}")
                implementation("com.sksamuel.hoplite:hoplite-hocon:${HOPLITE_VERSION}")
                // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-slf4j
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.8.0")
                // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jose", "~4.14.4"))
            }
        }
        val jsTest by getting {

        }
//        val nativeMain by getting
//        val nativeTest by getting
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
                    url = uri("https://maven.walt.id/repository/waltid/")
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



tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        listOf("-beta", "-alpha", "-rc").any { it in candidate.version.lowercase() } || candidate.version.takeLast(4).contains("RC")
    }
}
