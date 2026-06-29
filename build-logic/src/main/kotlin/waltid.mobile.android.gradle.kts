import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val waltidMobile = extensions.getByType<WaltidMobileLibraryExtension>()

val hasAndroidDeviceTests = layout.projectDirectory.dir("src/androidDeviceTest").asFile.isDirectory

kotlin {
    android {
        compileSdk = BuildConstants.COMPILE_SDK
        minSdk = BuildConstants.MIN_SDK
        withHostTestBuilder {}
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

extensions.configure<KotlinMultiplatformAndroidComponentsExtension>("androidComponents") {
    finalizeDsl {
        it.namespace = waltidMobile.androidNamespace.orNull
            ?: error("waltidMobile.androidNamespace must be configured for ${project.path}")
    }
}
