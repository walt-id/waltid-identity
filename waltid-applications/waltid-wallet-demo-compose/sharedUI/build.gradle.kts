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
            implementation("androidx.camera:camera-core:1.6.1")
            implementation("androidx.camera:camera-camera2:1.6.1")
            implementation("androidx.camera:camera-lifecycle:1.4.1")
            implementation("androidx.camera:camera-view:1.4.1")
            implementation("com.google.mlkit:barcode-scanning:17.3.0")
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.compose.material:material-icons-core:1.7.8")

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
