@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto2"

kotlin {
    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2 v1 migration")
        description.set("One-way waltid-crypto v1 key migration to crypto2")
    }
}
