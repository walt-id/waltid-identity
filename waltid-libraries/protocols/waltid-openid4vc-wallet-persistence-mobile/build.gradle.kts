@file:OptIn(
    ExperimentalAbiValidation::class,
    org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class,
)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
    alias(identityLibs.plugins.sqldelight)
}

group = "id.walt.protocols"

waltidMobile {
    androidNamespace.set("id.walt.wallet2.persistence")
}

kotlin {
    explicitApi()

    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    if (enableIosBuild) {
        swiftPMDependencies {
            iosMinimumDeploymentTarget.set("15.4")
            swiftPackage(
                url = url("https://github.com/sqlcipher/SQLCipher.swift.git"),
                version = exact("4.16.0"),
                products = listOf(
                    product(
                        "SQLCipher",
                        platforms = setOf(iOS()),
                        importedClangModules = setOf("SQLCipher"),
                    ),
                ),
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(identityLibs.sqldelight.runtime)
            implementation(identityLibs.sqldelight.coroutines)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        if (enableAndroidBuild) {
            androidMain.dependencies {
                implementation(identityLibs.sqldelight.android.driver)
                implementation(identityLibs.sqlcipher.android)
            }
        }
        if (enableIosBuild) {
            iosMain.dependencies {
                implementation(identityLibs.sqldelight.native.driver)
            }
        }
    }
}

sqldelight {
    linkSqlite.set(false)

    databases {
        create("WalletPersistenceDatabase") {
            packageName.set("id.walt.wallet2.persistence.db")
        }
    }
}
