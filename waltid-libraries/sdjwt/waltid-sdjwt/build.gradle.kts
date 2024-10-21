fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.petuska.npm.publish") version "3.4.3"
    `maven-publish`
    id("com.github.ben-manes.versions")
}

group = "id.walt.sdjwt"

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

    jvmToolchain(17)
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

            /*testTask {
                useKarma {
                    fun hasProgram(program: String) =
                        runCatching {
                            ProcessBuilder(program, "--version").start().waitFor()
                        }.getOrElse { -1 } == 0

                    val testEngine = mapOf(
                        "chromium" to { useChromiumHeadless() },
                        "firefox" to { useFirefoxHeadless() },
                        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" to { useChromeHeadless() }, // macOS
                        "chrome" to { useChromeHeadless() },
                        // what is it for Windows?
                    ).entries.firstOrNull { hasProgram(it.key) }
                    if (testEngine == null) println("No web test engine installed, please install chromium or firefox or chrome.")
                    else {
                        // println("Using web test engine: ${testEngine.key}")
                        testEngine.value.invoke()
                    }
                }
            }*/
        }
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }

    val hostOs = System.getProperty("os.name")
    val isMacOS = hostOs == "Mac OS X"
    val hostArch = System.getProperty("os.arch")
    if (hostOs in listOf("Windows", "Linux") && hostArch == "aarch64") {
        println("Native compilation is not yet supported for aarch64 on Windows / Linux.")
    } else {
        val isMingwX64 = hostOs.startsWith("Windows")

        if (enableIosBuild) {
            iosArm64()
            iosSimulatorArm64()
        }

        when {
            isMacOS -> {
               // macosX64("native")
            }

            hostOs == "Linux" -> linuxX64("native")
            isMingwX64 -> mingwX64("native")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }
    }

    sourceSets {

        all {
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }

        val commonMain by getting {
            dependencies {
                implementation("dev.whyoleg.cryptography:cryptography-random:0.3.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
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
                implementation("com.nimbusds:nimbus-jose-jwt:9.41.1")
                api(project(":waltid-libraries:crypto:waltid-crypto"))
            }
        }
        val jvmTest by getting {
            dependencies {
//              implementation("io.mockk:mockk:1.13.2")
                implementation("org.slf4j:slf4j-simple:2.0.16")
            }
        }
        val jsMain by getting {
            dependencies {
                // implementation(npm("jose", "~4.14.4"))
                implementation(npm("jose", "5.2.3"))
            }
        }
        val jsTest by getting {

        }

        //val nativeMain by getting
        //val nativeTest by getting

        if (enableIosBuild) {
            val iosArm64Main by getting
            val iosSimulatorArm64Main by getting
            val iosMain by creating {
                dependsOn(commonMain)
                iosArm64Main.dependsOn(this)
                iosSimulatorArm64Main.dependsOn(this)
            }

            val iosArm64Test by getting
            val iosSimulatorArm64Test by getting
            val iosTest by creating {
                dependsOn(commonTest)
                iosArm64Test.dependsOn(this)
                iosSimulatorArm64Test.dependsOn(this)
            }
        }
    }

    publishing {
        repositories {
            maven {
                val releasesRepoUrl = uri("https://maven.waltid.dev/releases")
                val snapshotsRepoUrl = uri("https://maven.waltid.dev/snapshots")
                url = uri(
                    if (version.toString()
                            .endsWith("SNAPSHOT")
                    ) snapshotsRepoUrl else releasesRepoUrl
                )
                val envUsername = System.getenv("MAVEN_USERNAME")
                val envPassword = System.getenv("MAVEN_PASSWORD")

                val usernameFile = File("secret_maven_username.txt")
                val passwordFile = File("secret_maven_password.txt")

                val secretMavenUsername = envUsername ?: usernameFile.let {
                    if (it.isFile) it.readLines().first() else ""
                }
                //println("Deploy username length: ${secretMavenUsername.length}")
                val secretMavenPassword = envPassword ?: passwordFile.let {
                    if (it.isFile) it.readLines().first() else ""
                }

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

tasks.named("jsBrowserTest") {
    enabled = false
}

npmPublish {
    registries {
        val envToken = System.getenv("NPM_TOKEN")
        val npmTokenFile = File("secret_npm_token.txt")
        val secretNpmToken =
            envToken ?: npmTokenFile.let { if (it.isFile) it.readLines().first() else "" }
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
