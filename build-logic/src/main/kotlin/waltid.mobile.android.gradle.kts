import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        compileSdk = BuildConstants.COMPILE_SDK
        minSdk = BuildConstants.MIN_SDK
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
