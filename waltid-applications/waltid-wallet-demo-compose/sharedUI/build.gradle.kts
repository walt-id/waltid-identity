@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import java.io.File
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension

plugins {
    id("waltid.mobile.library")
    kotlin("native.cocoapods") apply false
    alias(identityLibs.plugins.compose.multiplatform)
    kotlin("plugin.compose")
}

group = "id.walt.walletdemo.compose"

val enableIosBuild = providers.gradleProperty("enableIosBuild").orNull.toBoolean()
val isCocoaPodsBuild = providers.gradleProperty("kotlin.native.cocoapods.platform").isPresent
val enableCocoaPods = enableIosBuild || isCocoaPodsBuild

if (enableCocoaPods) {
    apply(plugin = "org.jetbrains.kotlin.native.cocoapods")
}

kotlin {
    android {
        namespace = "id.walt.walletdemo.compose.ui"

        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedLogic"))
            implementation(identityLibs.compose.runtime)
            implementation(identityLibs.compose.foundation)
            implementation(identityLibs.compose.ui)
            implementation(identityLibs.compose.material3)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val mobileUiTest by creating {
            dependsOn(commonTest.get())

            dependencies {
                implementation(identityLibs.kotlinx.coroutines.test)
                implementation(identityLibs.compose.ui.test)
            }
        }

        if (enableIosBuild) {
            val iosTest by getting {
                dependsOn(mobileUiTest)
            }
        }

        val androidHostTest by getting {
            dependsOn(mobileUiTest)

            dependencies {
                implementation(identityLibs.junit)
                implementation(identityLibs.robolectric)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        useJUnit()
    }
}

if (enableCocoaPods) {
    extensions.getByType<KotlinMultiplatformExtension>().extensions.configure<CocoapodsExtension>("cocoapods") {
        summary = "Shared Compose UI for the walt.id wallet demo"
        homepage = "https://walt.id"
        version = "1.0"
        ios.deploymentTarget = "15.4"
        framework {
            baseName = "sharedUI"
            isStatic = true
        }
        extraSpecAttributes["libraries"] = "'c++', 'sqlite3'"
    }

    val sharedUiPodspecPath = layout.projectDirectory.file("sharedUI.podspec").asFile.absolutePath

    tasks.named("podspec") {
        doLast {
            val podspec = File(sharedUiPodspecPath)
            val marker = "                    -PenableIosBuild=true \\\n"
            val text = podspec.readText()
            if (marker !in text) {
                podspec.writeText(
                    text.replace(
                        "                    -Pkotlin.native.cocoapods.platform=${'$'}PLATFORM_NAME \\",
                        marker + "                    -Pkotlin.native.cocoapods.platform=${'$'}PLATFORM_NAME \\",
                    )
                )
            }
        }
    }
} else {
    tasks.register("podspec") {
        group = "cocoapods"
        description = "No-op placeholder when iOS/CocoaPods build support is disabled."
        doLast {
            logger.lifecycle("Skipping sharedUI podspec generation because enableIosBuild is false.")
        }
    }
}
