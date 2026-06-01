@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
}

group = "id.walt.protocols"

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")

kotlin {
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "id.walt.wallet2.client"
        compileSdk = 35
        minSdk = 28

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence"))
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(identityLibs.findLibrary("kotlinx-coroutines-core").get())
            implementation(identityLibs.findLibrary("kotlinx-serialization-json").get())
            implementation(identityLibs.findLibrary("kotlinx-datetime").get())
            implementation(identityLibs.findLibrary("ktor-client-core").get())
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.findLibrary("kotlinx-coroutines-test").get())
        }

        iosMain.dependencies {
            implementation(identityLibs.findLibrary("ktor-client-darwin").get())
        }
    }
}
