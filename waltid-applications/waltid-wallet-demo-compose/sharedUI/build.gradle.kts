@file:OptIn(ExperimentalWasmDsl::class)

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
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
            implementation(identityLibs.compose.navigation3.ui)
            implementation(identityLibs.coil.compose)
            implementation(identityLibs.coil.network.ktor3)
            implementation(compose.components.resources)
        }

        if (enableAndroidBuild || enableIosBuild) {
            val mobileMain by creating {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(identityLibs.easyqrscan)
                }
            }

            if (enableAndroidBuild) {
                androidMain.get().dependsOn(mobileMain)
                androidMain.dependencies {
                    // The system back gesture is registered against the host Activity's own
                    // dispatcher, so a provider surface can turn it into an Activity result.
                    implementation(identityLibs.androidx.activity.compose)
                }
            }

            if (enableIosBuild) {
                iosMain.get().dependsOn(mobileMain)
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

compose {
    resources {
        publicResClass = true
        packageOfResClass = "id.walt.walletdemo.compose.ui.resources"
    }
}

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        useJUnit()
    }
}

// Asset processing is off by default for Android KMP libraries. Compose Multiplatform resources
// copy into those assets; without this the generated waltid_logo never reaches the APK.
if (enableAndroidBuild) {
    extensions.configure<KotlinMultiplatformAndroidComponentsExtension>("androidComponents") {
        finalizeDsl { android ->
            android.androidResources.enable = true
        }
    }
}
