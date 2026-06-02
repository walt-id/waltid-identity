@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.base.library")

    kotlin("multiplatform")
    kotlin("plugin.power-assert")
}

kotlin {
    jvmToolchain(project.javaLibraryVersion)
    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

powerAssert {
    includedSourceSets = listOf("commonTest")
    functions = WaltidBuildConstants.POWER_ASSERT_FUNCTIONS
}
