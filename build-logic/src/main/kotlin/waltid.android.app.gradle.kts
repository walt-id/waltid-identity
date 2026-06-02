import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

android {
    namespace = project.group.toString()

    compileSdk = WaltidBuildConstants.COMPILE_SDK
    defaultConfig { minSdk = WaltidBuildConstants.MIN_SDK }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(project.javaLibraryVersion)
        targetCompatibility = JavaVersion.toVersion(project.javaLibraryVersion)
    }
    packaging {
        resources {
            excludes += WaltidBuildConstants.META_INF_EXCLUDES
        }
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(project.javaLibraryVersion.toString()))
        }
    }
}
