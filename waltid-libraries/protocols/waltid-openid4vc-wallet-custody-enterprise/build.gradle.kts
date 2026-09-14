@file:OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)

plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
}

group = "id.walt.protocols"
waltidMobile { androidNamespace.set("id.walt.wallet2.custody.enterprise") }

kotlin {
    explicitApi()
    abiValidation { binariesSource.set(org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource.MAIN_COMPILATION) }
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile"))
            api(identityLibs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.mock)
        }
    }
}
