@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(identityLibs.plugins.compose.multiplatform)
    kotlin("plugin.compose")
}

group = "id.walt.walletdemo.compose"

kotlin {
    wasmJs {
        outputModuleName = "walletDemoCompose"
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedLogic"))
            implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedUI"))
            implementation(identityLibs.compose.runtime)
            implementation(identityLibs.compose.ui)
        }
    }
}
