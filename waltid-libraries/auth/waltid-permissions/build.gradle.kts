import love.forte.plugin.suspendtrans.configuration.ClassInfo
import love.forte.plugin.suspendtrans.configuration.SuspendTransformConfiguration
import love.forte.plugin.suspendtrans.configuration.TargetPlatform
import love.forte.plugin.suspendtrans.gradle.SuspendTransPluginConstants
import love.forte.plugin.suspendtrans.gradle.SuspendTransformPluginExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.5.3"
    id("com.github.ben-manes.versions")
    id("love.forte.plugin.suspend-transform")
}

group = "id.walt.permissions"

repositories {
    mavenCentral()
}

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }

    includeAnnotation = false // Required in the current version to avoid "compileOnly" warning
}

kotlin {
    jvm {

    }
    js(IR) {
        outputModuleName = "waltid-permissions"
        nodejs {
            generateTypeScriptDefinitions()
        }
        browser {
            //generateTypeScriptDefinitions()
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
                        testEngine.value.invoke()
                    }
                }
            }*/
        }
        binaries.library()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("${SuspendTransPluginConstants.ANNOTATION_GROUP}:${SuspendTransPluginConstants.ANNOTATION_NAME}:${SuspendTransPluginConstants.ANNOTATION_VERSION}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id permissions")
                description.set(
                    """
                    Kotlin/Java library for permissions
                    """.trimIndent()
                )
                url.set("https://walt.id")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("walt.id")
                        name.set("walt.id")
                        email.set("office@walt.id")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(if (version.toString().endsWith("SNAPSHOT")) uri("https://maven.waltid.dev/snapshots") else uri("https://maven.waltid.dev/releases"))
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: File("$rootDir/secret_maven_username.txt").let { if (it.isFile) it.readLines().first() else "" }
                password = System.getenv("MAVEN_PASSWORD") ?: File("$rootDir/secret_maven_password.txt").let { if (it.isFile) it.readLines().first() else "" }
            }
        }
    }
}

tasks.named("jsBrowserTest") {
    enabled = false
}

/*extensions.getByType<SuspendTransformGradleExtension>().apply {
    transformers[TargetPlatform.JS] = mutableListOf(
        SuspendTransformConfiguration.jsPromiseTransformer.copy(
            copyAnnotationExcludes = listOf(
                ClassInfo("kotlin.js", "JsExport.Ignore")
            )
        )
    )
}*/

npmPublish {
    registries {
        val envToken = System.getenv("NPM_TOKEN")
        val npmTokenFile = File("secret_npm_token.txt")
        val secretNpmToken =
            envToken ?: npmTokenFile.let { if (it.isFile) it.readLines().first() else "" }
        val hasNPMToken = secretNpmToken.isNotEmpty()
        val isReleaseBuild = Regex("\\d+.\\d+.\\d+").matches(version.get())
        if (isReleaseBuild && hasNPMToken) {
            readme.set(File("README-JS.md"))
            register("npmjs") {
                uri.set(uri("https://registry.npmjs.org"))
                authToken.set(secretNpmToken)
            }
        }
    }
}
