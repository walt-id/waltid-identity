@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

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

if (enableIosBuild) {
    kotlin.targets.withType<KotlinNativeTarget>().configureEach {
        val sdk = when (name) {
            "iosArm64" -> "iphoneos"
            else -> "iphonesimulator"
        }

        binaries.withType<TestExecutable>().configureEach {
            val targetIosProject = project(":waltid-libraries:crypto:waltid-target-ios")
            val frameworkPath = targetIosProject.layout.buildDirectory
                .dir("cocoapods/synthetic/ios/build/Debug-$sdk/JOSESwift")
                .get()
                .asFile
                .absolutePath

            linkerOpts("-F$frameworkPath", "-framework", "JOSESwift", "-rpath", frameworkPath, "-lsqlite3")
        }
    }
}
