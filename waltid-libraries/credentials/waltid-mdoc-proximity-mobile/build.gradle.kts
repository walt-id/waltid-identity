@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
}

group = "id.walt.credentials"

waltidMobile {
    androidNamespace.set("id.walt.mdoc.proximity.mobile")
}

kotlin {
    explicitApi()

    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:credentials:waltid-mdoc-proximity"))
            implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
            implementation(identityLibs.kotlinx.atomicfu)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}
