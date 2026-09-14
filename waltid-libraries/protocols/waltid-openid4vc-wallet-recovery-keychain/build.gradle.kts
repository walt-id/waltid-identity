@file:OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)

plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
}

group = "id.walt.protocols"
waltidMobile { androidNamespace.set("id.walt.wallet2.recovery.keychain") }

kotlin {
    explicitApi()
    abiValidation { binariesSource.set(org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource.MAIN_COMPILATION) }
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile"))
            implementation(identityLibs.kotlinx.coroutines.core)
        }
    }
}
