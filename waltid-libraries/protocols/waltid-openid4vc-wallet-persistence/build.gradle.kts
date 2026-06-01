@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id("app.cash.sqldelight") version "2.0.2"
}

group = "id.walt.protocols"

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")

kotlin {
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "id.walt.wallet2.persistence"
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
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
            implementation("app.cash.sqldelight:runtime:2.0.2")
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
            implementation(identityLibs.findLibrary("kotlinx-coroutines-core").get())
            implementation(identityLibs.findLibrary("kotlinx-serialization-json").get())
            implementation(identityLibs.findLibrary("kotlinx-datetime").get())
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.findLibrary("kotlinx-coroutines-test").get())
        }

        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.0.2")
        }

        iosMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto-ios"))
            implementation("app.cash.sqldelight:native-driver:2.0.2")
        }
    }
}

sqldelight {
    databases {
        create("WalletPersistenceDatabase") {
            packageName.set("id.walt.wallet2.persistence.db")
        }
    }
}
