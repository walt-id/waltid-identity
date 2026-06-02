@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.base.library")

    kotlin("jvm")
    kotlin("plugin.power-assert")
}

kotlin {
    jvmToolchain(project.javaLibraryVersion)
}

powerAssert {
    includedSourceSets = listOf("test")
    functions = WaltidBuildConstants.POWER_ASSERT_FUNCTIONS
}
