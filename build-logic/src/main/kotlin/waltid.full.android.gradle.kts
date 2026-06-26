import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = project.group.toString()
        compileSdk = BuildConstants.COMPILE_SDK
        minSdk = BuildConstants.MIN_SDK

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
        withHostTestBuilder { }

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

    sourceSets {
        androidMain.get().dependsOn(named("jvmAndroidMain").get())
    }
}
