import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val moduleNamespace = when (project.path) {
    ":waltid-applications:waltid-wallet-demo-compose:sharedLogic" -> "id.walt.walletdemo.compose.logic"
    ":waltid-applications:waltid-wallet-demo-compose:sharedUI" -> "id.walt.walletdemo.compose.ui"
    ":waltid-libraries:protocols:waltid-mobile-test-utils" -> "id.walt.mobile.test"
    ":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile" -> "id.walt.wallet2.mobile"
    ":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile" -> "id.walt.wallet2.persistence"
    else -> project.group.toString()
}

val hasAndroidHostTests = layout.projectDirectory.dir("src/androidHostTest").asFile.isDirectory
val hasAndroidDeviceTests = layout.projectDirectory.dir("src/androidDeviceTest").asFile.isDirectory

kotlin {
    android {
        namespace = moduleNamespace
        compileSdk = BuildConstants.COMPILE_SDK
        minSdk = BuildConstants.MIN_SDK
        withHostTestBuilder {}.configure {
            if (hasAndroidHostTests) {
                isIncludeAndroidResources = true
            }
        }
        if (hasAndroidDeviceTests) {
            withDeviceTestBuilder {
                sourceSetTreeName = "androidDeviceTest"
            }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                packaging {
                    resources.excludes.add("META-INF/DEPENDENCIES")
                    resources.excludes.add("META-INF/LICENSE.md")
                    resources.excludes.add("META-INF/NOTICE.md")
                }
            }
        }
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
}
