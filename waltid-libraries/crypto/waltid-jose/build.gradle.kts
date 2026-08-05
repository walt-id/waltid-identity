@file:OptIn(ExperimentalAbiValidation::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

val chromeExecutable = providers.environmentVariable("CHROME_BIN").orNull
    ?: listOf("/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser")
        .firstOrNull { file(it).isFile }

plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto2"

kotlin {
    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    wasmJs {
        browser {
            testTask {
                enabled = chromeExecutable != null
                chromeExecutable?.let { environment("CHROME_BIN", it) }
                useKarma { useChromeHeadless() }
            }
        }
        nodejs()
    }

    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(identityLibs.nimbus.jose.jwt)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id JOSE")
        description.set("Multiplatform JOSE implementation for walt.id crypto2")
    }
}
