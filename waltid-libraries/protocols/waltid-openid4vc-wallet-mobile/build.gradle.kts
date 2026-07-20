@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
    alias(identityLibs.plugins.skie)
}

group = "id.walt.protocols"

waltidMobile {
    androidNamespace.set("id.walt.wallet2.mobile")
}

skie {
    analytics {
        enabled.set(false)
    }

    build {
        produceDistributableFramework()
    }
}

kotlin {
    explicitApi()

    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    if (enableIosBuild) {
        val walletCoreXcFramework = XCFramework("WalletCore")

        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
            binaries.framework {
                baseName = "WalletCore"
                isStatic = true
                binaryOption("bundleId", "id.walt.wallet.core")
                walletCoreXcFramework.add(this)
            }

        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile"))
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:protocols:waltid-openid4vp-clientidprefix"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        if (enableAndroidBuild) {
            androidMain.dependencies {
                implementation(identityLibs.ktor.client.android)
            }
        }
        if (enableIosBuild) {
            iosMain.dependencies {
                implementation(identityLibs.ktor.client.darwin)
            }
        }
        if (enableAndroidBuild) {
            val androidDeviceTest by getting {
                dependencies {
                    implementation(kotlin("test"))
                    implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
                    implementation(identityLibs.kotlinx.coroutines.test)
                    implementation(identityLibs.androidx.test.runner)
                    implementation(identityLibs.androidx.test.ext.junit)
                    implementation(identityLibs.ktor.client.android)
                }
            }
        }
    }
}
