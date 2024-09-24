plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.4.3"
    id("com.github.ben-manes.versions")
}

group = "id.walt.authkit"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {

    }
    js(IR) {
        moduleName = "waltid-authkit-permissions"
        browser {
            generateTypeScriptDefinitions()
            testTask {
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
            }
        }
        binaries.library()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

//dependencies {
//    testImplementation(kotlin("test"))
//}
//
//tasks.test {
//    useJUnitPlatform()
//}
//kotlin {
//    jvmToolchain(21)
//}
