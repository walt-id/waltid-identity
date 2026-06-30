@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.multiplatform.library.common")
    id("love.forte.plugin.suspend-transform")
}

kotlin {
    jvm()

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
        if (enableAndroidBuild) {
            androidMain.get().dependsOn(jvmAndroidMain)
        }

        val jvmAndroidTest by creating {
            dependsOn(commonTest.get())
        }
        jvmTest.get().dependsOn(jvmAndroidTest)
    }
}

if (enableAndroidBuild) {
    pluginManager.apply("waltid.full.android")
}

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }
}
