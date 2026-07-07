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
            implementation(identityLibs.androidx.camera.core)
            implementation(identityLibs.androidx.camera.camera2)
            implementation(identityLibs.androidx.camera.lifecycle)
            implementation(identityLibs.androidx.camera.view)
            implementation(identityLibs.mlkit.barcode.scanning)
            implementation(identityLibs.androidx.activity.compose)
            implementation(identityLibs.compose.material.icons.core)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            implementation("androidx.compose.material:material-icons-extended:1.7.8")


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
