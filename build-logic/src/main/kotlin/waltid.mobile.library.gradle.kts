@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("waltid.base.library")
    kotlin("multiplatform")
    kotlin("plugin.power-assert")
    id("com.android.kotlin.multiplatform.library")
}

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(project.javaLibraryVersion)

    androidLibrary {
        compileSdk = WaltidBuildConstants.COMPILE_SDK
        minSdk = WaltidBuildConstants.MIN_SDK
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

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// iOS test binaries cannot link without CocoaPods framework paths (JOSESwift from waltid-target-ios).
if (enableIosBuild) {
    tasks.matching { it.name.startsWith("linkDebugTestIos") || it.name.startsWith("linkReleaseTestIos") }.configureEach {
        enabled = false
    }
}

powerAssert {
    includedSourceSets = listOf("commonTest")
    functions = WaltidBuildConstants.POWER_ASSERT_FUNCTIONS
}
