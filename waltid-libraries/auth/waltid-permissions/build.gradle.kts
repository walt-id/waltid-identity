plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.4.3"
    id("com.github.ben-manes.versions")
}

group = "id.walt.permissions"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {

    }
    js(IR) {
        moduleName = "waltid-permissions"
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                name.set("walt.id permissions")
                description.set(
                    """
                    Kotlin/Java library for permissions
                    """.trimIndent()
                )
                url.set("https://walt.id")
            }
            from(components["java"])
        }
    }

    repositories {
        maven {
            val releasesRepoUrl = uri("https://maven.waltid.dev/releases")
            val snapshotsRepoUrl = uri("https://maven.waltid.dev/snapshots")
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            val envUsername = System.getenv("MAVEN_USERNAME")
            val envPassword = System.getenv("MAVEN_PASSWORD")

            val usernameFile = File("secret_maven_username.txt")
            val passwordFile = File("secret_maven_password.txt")

            val secretMavenUsername = envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" }
            val secretMavenPassword = envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }

            credentials {
                username = secretMavenUsername
                password = secretMavenPassword
            }
        }
    }
}

