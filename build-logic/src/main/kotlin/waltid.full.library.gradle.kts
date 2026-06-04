@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("waltid.multiplatform.library.common")
    id("com.android.kotlin.multiplatform.library")
    id("love.forte.plugin.suspend-transform")
}

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    jvm()

    androidLibrary {
        namespace = project.group.toString()
        compileSdk = WaltidBuildConstants.COMPILE_SDK
        minSdk = WaltidBuildConstants.MIN_SDK

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
        withHostTestBuilder { }

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(project.javaLibraryVersion.toString()))
                }
            }
        }

        packaging {
            resources {
                excludes += WaltidBuildConstants.META_INF_EXCLUDES
            }
        }
    }

    js(IR) {
        useCommonJs()
        generateTypeScriptDefinitions()
        binaries.library()

        nodejs {
            testTask {
                failOnNoDiscoveredTests = false
                useMocha { timeout = "30s" }
            }
        }
        browser {
            testTask { enabled = false }
        }
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        val jvmAndroidTest by creating {
            dependsOn(commonTest.get())
        }
        jvmTest.get().dependsOn(jvmAndroidTest)
    }
}

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }
}
