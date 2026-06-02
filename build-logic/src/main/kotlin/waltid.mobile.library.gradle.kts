@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("waltid.base.library")
    kotlin("multiplatform")
    kotlin("plugin.power-assert")
    id("com.android.kotlin.multiplatform.library")
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")
val javaVersion = identityLibs.findVersion("java-library").get().requiredVersion.toInt()

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(javaVersion)

    androidLibrary {
        compileSdk = 37
        minSdk = 30
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
                }
            }
        }
        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    functions = listOf(
        "kotlin.assert", "kotlin.test.assertEquals", "kotlin.test.assertNull",
        "kotlin.test.assertTrue", "kotlin.test.assertFalse", "kotlin.test.assertContentEquals",
        "kotlin.require", "kotlin.check"
    )
}
