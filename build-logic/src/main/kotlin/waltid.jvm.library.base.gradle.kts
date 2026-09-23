@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.base.library")

    kotlin("jvm")
    kotlin("plugin.power-assert")
    id("waltid.licensee")
}

kotlin {
    jvmToolchain(project.javaLibraryVersion)
}

powerAssert {
    includedSourceSets = listOf("test")
    functions = BuildConstants.POWER_ASSERT_FUNCTIONS
}
