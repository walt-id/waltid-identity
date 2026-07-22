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

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(project(":waltid-libraries:crypto:waltid-jose"))
            implementation(project(":waltid-libraries:crypto:waltid-cose"))
        }

        if (enableAndroidBuild || enableIosBuild) {
            val mobileMain by creating {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(identityLibs.signum.indispensable)
                    implementation(identityLibs.signum.supreme)
                }
            }
            if (enableAndroidBuild) {
                androidMain.get().dependsOn(mobileMain)
                named("androidDeviceTest") {
                    dependencies {
                        implementation(kotlin("test"))
                        implementation(identityLibs.kotlinx.coroutines.test)
                    }
                }
            }
            if (enableIosBuild) {
                iosMain.get().dependsOn(mobileMain)
                named("iosTest") {
                    dependencies {
                        implementation(kotlin("test"))
                        implementation(identityLibs.kotlinx.coroutines.test)
                    }
                }
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2 Signum provider")
        description.set("Android KeyStore and iOS Keychain provider for walt.id crypto2")
    }
}
