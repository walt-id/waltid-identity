@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.base.library")

    kotlin("jvm")
    kotlin("plugin.power-assert")
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")
val javaVersion = identityLibs.findVersion("java-library").get().requiredVersion.toInt()

kotlin {
    jvmToolchain(javaVersion)
}

powerAssert {
    includedSourceSets = listOf("test")
    functions = listOf(
        // kotlin.test
        "kotlin.assert", "kotlin.test.assertEquals", "kotlin.test.assertNull", "kotlin.test.assertTrue", "kotlin.test.assertFalse",
        "kotlin.test.assertContentEquals",

        // checks
        "kotlin.require", "kotlin.check"
    )
}
