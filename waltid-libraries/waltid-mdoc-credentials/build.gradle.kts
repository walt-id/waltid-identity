plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.petuska.npm.publish") version "3.4.3"
    `maven-publish`
}

group = "id.walt"

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
        }
    }

    jvmToolchain(16)
    jvm {
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        /*browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }*/
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    /*val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }*/

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
                //implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.5.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.augustcellars.cose:cose-java:1.1.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.bouncycastle:bcprov-lts8on:2.73.6")
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.6")
                implementation("io.mockk:mockk:1.13.11")

                implementation(kotlin("reflect"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("cose-js", "0.9.0"))
            }
        }
        val jsTest by getting {
        }
        //val nativeMain by getting
        //val nativeTest by getting
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
            readme.set(File("README.md"))
            register("npmjs") {
                uri.set(uri("https://registry.npmjs.org"))
                authToken.set(secretNpmToken)
            }
        }
    }
}
