plugins {
    id("waltid.multiplatform.library.common")

    id("love.forte.plugin.suspend-transform")
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")
val javaVersion = identityLibs.findVersion("java-library").get().requiredVersion.toInt()

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    jvm()

    js(IR) {
        useEsModules()
        generateTypeScriptDefinitions()

        binaries.library() // Produce library instead of standalone application


        nodejs {
            testTask {
                failOnNoDiscoveredTests = false

                useMocha {
                    timeout = "30s"
                }
            }
        }
        browser {
            testTask {
                enabled = false
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
                        testEngine.value.invoke()
                    }
                }
            }*/
        }
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }
}

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }

    //includeAnnotation = false // Required in the current version to avoid "compileOnly" warning
}
