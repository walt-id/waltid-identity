@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.base.library")
    kotlin("multiplatform")
    kotlin("plugin.power-assert")
}

extensions.create<WaltidMobileLibraryExtension>("waltidMobile")

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(project.javaLibraryVersion)

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

if (enableAndroidBuild) {
    pluginManager.apply("waltid.mobile.android")
}

if (project.file("src/commonTest").exists()) {
    powerAssert {
        includedSourceSets = listOf("commonTest")
        functions = BuildConstants.POWER_ASSERT_FUNCTIONS
    }
}
