@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("waltid.mobile.library")
}

group = "id.walt.walletdemo.compose"

kotlin {
    androidLibrary {
        namespace = "id.walt.walletdemo.compose.logic"
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile"))
            implementation(identityLibs.ktor.client.android)
        }

        iosMain.dependencies {
            implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile"))
            implementation(identityLibs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}

// iOS test binaries cannot link without CocoaPods framework paths from transitive iOS crypto deps.
// Keep iOS source/test compilation enabled, but skip native test executable linking.
tasks.matching { it.name.startsWith("linkDebugTestIos") || it.name.startsWith("linkReleaseTestIos") }.configureEach {
    enabled = false
}
