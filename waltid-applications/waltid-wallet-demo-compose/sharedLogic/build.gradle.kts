@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("waltid.mobile.library")
}

waltidMobile {
    androidNamespace.set("id.walt.walletdemo.compose.logic")
}

group = "id.walt.walletdemo.compose"

val enableMobileWallet = enableAndroidBuild || enableIosBuild

kotlin {
    if (enableWalletDemoComposeWeb) {
        wasmJs {
            browser()
            binaries.executable()
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.kotlinx.coroutines.core)
        }

        if (enableMobileWallet) {
            val mobileMain by creating {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile"))
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                }
            }

            if (enableAndroidBuild) {
                androidMain {
                    dependsOn(mobileMain)
                }

                androidMain.dependencies {
                    implementation(identityLibs.ktor.client.android)
                }
            }

            if (enableIosBuild) {
                iosMain {
                    dependsOn(mobileMain)
                }

                iosMain.dependencies {
                    implementation(identityLibs.ktor.client.darwin)
                }
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}

// iOS test binaries do not get the Xcode SwiftPM linkage package used by the demo app.
// Keep iOS source/test compilation enabled, but skip native test executable linking.
tasks.matching { it.name.startsWith("linkDebugTestIos") || it.name.startsWith("linkReleaseTestIos") }.configureEach {
    enabled = false
}
