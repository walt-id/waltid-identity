@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("waltid.mobile.library")
    alias(identityLibs.plugins.compose.multiplatform)
    kotlin("plugin.compose")
}

waltidMobile {
    androidNamespace.set("id.walt.walletdemo.compose.ui")
}

group = "id.walt.walletdemo.compose"

kotlin {
    if (enableWalletDemoComposeWeb) {
        wasmJs {
            browser()
            binaries.executable()
        }
    }

    if (enableIosBuild) {
        targets.withType<KotlinNativeTarget>().configureEach {
            binaries.framework {
                baseName = "sharedUI"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedLogic"))
            implementation(identityLibs.compose.runtime)
            implementation(identityLibs.compose.foundation)
            implementation(identityLibs.compose.ui)
            implementation(identityLibs.compose.material3)
            implementation(identityLibs.compose.material.icons.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }

        if (enableAndroidBuild || enableIosBuild) {
            val mobileMain by creating {
                dependsOn(commonMain.get())
                dependencies {
                    api(identityLibs.easyqrscan)
                }
            }

            if (enableAndroidBuild) {
                androidMain {
                    dependsOn(mobileMain)
                    dependencies {
                        implementation(identityLibs.androidx.activity.compose)
                        implementation("androidx.compose.material:material-icons-extended:1.7.8")
                    }
                }
            }

            if (enableIosBuild) {
                iosMain {
                    dependsOn(mobileMain)
                }
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        if (enableAndroidBuild || enableIosBuild) {
            val mobileUiTest by creating {
                dependsOn(commonTest.get())

                dependencies {
                    implementation(identityLibs.kotlinx.coroutines.test)
                    implementation(identityLibs.compose.ui.test)
                }
            }

            if (enableIosBuild) {
                val iosTest by getting {
                    dependsOn(mobileUiTest)
                }
            }

            if (enableAndroidBuild) {
                val androidHostTest by getting {
                    dependsOn(mobileUiTest)

                    dependencies {
                        implementation(identityLibs.junit)
                        implementation(identityLibs.robolectric)
                    }
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        useJUnit()
    }
}
