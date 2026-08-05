@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

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

    js {
        browser {
            testTask {
                enabled = true
                chromeExecutable?.let { environment("CHROME_BIN", it) }
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-jose"))
            implementation(project(":waltid-libraries:crypto:waltid-cose"))
            implementation(identityLibs.ktor.client.mock)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2 KMS providers")
        description.set("Multiplatform REST KMS providers for walt.id crypto2")
    }
}
