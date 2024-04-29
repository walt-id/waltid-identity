import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")

    kotlin("plugin.serialization")

    id("dev.petuska.npm.publish") version "3.4.2"
    `maven-publish`
    id("com.github.ben-manes.versions")
}

group = "id.walt"

repositories {
    mavenCentral()
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targets.configureEach {
        compilations.configureEach {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    jvm {
        jvmToolchain(15) // 16 possible?
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

            testTask {
                useKarma {
                    fun hasProgram(program: String) =
                        runCatching { ProcessBuilder(program, "--version").start().waitFor() }.getOrElse { -1 } == 0

                    val testEngine = mapOf(
                        "chromium" to { useChromiumHeadless() },
                        "chrome" to { useChromeHeadless() },
                        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" to { useChromeHeadless() }, // macOS
                        "firefox" to { useFirefoxHeadless() }
                    ).entries.firstOrNull { hasProgram(it.key) }
                    if (testEngine == null) println("No web test engine installed, please install chromium or firefox or chrome.")
                    else {
                        println("Using web test engine: ${testEngine.key}")
                        testEngine.value.invoke()
                    }
                }
            }
        }
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }

    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    if (hostOs in listOf("Windows", "Linux") && hostArch == "aarch64") {
        println("Native compilation is not yet supported for aarch64 on Windows / Linux.")
    } else {
        val isMingwX64 = hostOs.startsWith("Windows")
        val nativeTarget = when {
            hostOs == "Mac OS X" -> macosX64("native")
            hostOs == "Linux" -> linuxX64("native")
            isMingwX64 -> mingwX64("native")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }

        when (hostOs) {
            "Mac OS X" -> listOf<KotlinNativeTarget>(
//                iosArm64(),
//                iosX64(),
//                iosSimulatorArm64()
            )

            else -> listOf()
        }.forEach {
            println("Native compilation for target: ${it.name}")
            val platform = when (it.name) {
                "iosArm64" -> "iphoneos"
                else -> "iphonesimulator"
            }

            it.binaries.framework {
                baseName = "shared"
            }

            it.compilations.getByName("main") {
//            cinterops.create("id.walt.sdjwt.cinterop.ios") {
//                val interopTask = tasks[interopProcessingTaskName]
//                interopTask.dependsOn(":waltid-sd-jwt-ios:build${platform.uppercase()}")
//
//                defFile("$projectDir/src/nativeInterop/cinterop/waltid-sd-jwt-ios.def")
//                packageName("id.walt.sdjwt.cinterop.ios")
//                includeDirs("$projectDir/waltid-sd-jwt-ios/build/Release-$platform/include/")
//
//                headers("$projectDir/waltid-sd-jwt-ios/build/Release-$platform/include/waltid_sd_jwt_ios/waltid_sd_jwt_ios-Swift.h")
//            }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("dev.whyoleg.cryptography:cryptography-random:0.3.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:5.8.0")

                implementation("io.kotest:kotest-assertions-json:5.8.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
            }
        }
        val jvmTest by getting {
            dependencies {
//              implementation("io.mockk:mockk:1.13.2")

                implementation("io.kotest:kotest-runner-junit5:5.8.0")
                implementation("io.kotest:kotest-assertions-core:5.8.0")
                implementation("io.kotest:kotest-assertions-json:5.8.0")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jose", "~4.14.4"))
            }
        }
        val jsTest by getting {

        }
        //val nativeMain by getting
        //val nativeTest by getting

        if (hostOs == "Mac OS X") {
//            val iosArm64Main by getting
//            val iosSimulatorArm64Main by getting
            /*val iosX64Main by getting
            val iosMain by creating {
                dependsOn(commonMain)
//                iosArm64Main.dependsOn(this)
//                iosSimulatorArm64Main.dependsOn(this)
                iosX64Main.dependsOn(this)
            }*/
//            val iosArm64Test by getting
//            val iosSimulatorArm64Test by getting
            /*val iosX64Test by getting
            val iosTest by creating {
                dependsOn(commonTest)
//                iosArm64Test.dependsOn(this)
//                iosSimulatorArm64Test.dependsOn(this)
                iosX64Test.dependsOn(this)
            }*/
        }
    }

    publishing {
        repositories {
            maven {
                url = uri("https://maven.waltid.dev/releases")
                val envUsername = System.getenv("MAVEN_USERNAME")
                val envPassword = System.getenv("MAVEN_PASSWORD")

                val usernameFile = File("secret_maven_username.txt")
                val passwordFile = File("secret_maven_password.txt")

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
}

npmPublish {
    registries {
        val envToken = System.getenv("NPM_TOKEN")
        val npmTokenFile = File("secret_npm_token.txt")
        val secretNpmToken = envToken ?: npmTokenFile.let { if (it.isFile) it.readLines().first() else "" }
        val hasNPMToken = secretNpmToken.isNotEmpty()
        val isReleaseBuild = Regex("\\d+.\\d+.\\d+").matches(version.get())
        if (isReleaseBuild && hasNPMToken) {
            readme.set(File("NPM_README.md"))
            register("npmjs") {
                uri.set(uri("https://registry.npmjs.org"))
                authToken.set(secretNpmToken)
            }
        }
    }
}
