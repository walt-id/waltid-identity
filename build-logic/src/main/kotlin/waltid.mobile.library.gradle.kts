@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("waltid.base.library")
    kotlin("multiplatform")
    kotlin("plugin.power-assert")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(project.javaLibraryVersion)

    android {
        compileSdk = BuildConstants.COMPILE_SDK
        minSdk = BuildConstants.MIN_SDK
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(project.javaLibraryVersion.toString()))
                }
            }
        }
        packaging {
            resources {
                excludes += BuildConstants.META_INF_EXCLUDES
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

if (project.file("src/commonTest").exists()) {
    powerAssert {
        includedSourceSets = listOf("commonTest")
        functions = BuildConstants.POWER_ASSERT_FUNCTIONS
    }
}
