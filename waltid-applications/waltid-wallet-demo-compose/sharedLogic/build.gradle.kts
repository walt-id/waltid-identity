@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("waltid.mobile.library")
}

waltidMobile {
    androidNamespace.set("id.walt.walletdemo.compose.logic")
}

group = "id.walt.walletdemo.compose"

val enableMobileWallet = enableAndroidBuild || enableIosBuild

kotlin {
    if (enableWalletDemoComposeWeb) {
        wasmJs {
            browser {
                testTask {
                    useKarma { useChromeHeadless() }
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.ktor.http)
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.ktor.client.content.negotiation)
            implementation(identityLibs.ktor.serialization.kotlinx.json)
        }

        if (enableMobileWallet) {
            val mobileMain = create("mobileMain") {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile"))
                    implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
                    implementation(identityLibs.ktor.client.core)
                    implementation(identityLibs.ktor.client.content.negotiation)
                    implementation(identityLibs.ktor.serialization.kotlinx.json)
                    implementation(identityLibs.cryptography.core)
                    implementation(identityLibs.okio)
                    implementation(identityLibs.whyoleg.cryptography.random)
                }
            }

            val mobileTest = create("mobileTest") {
                dependsOn(commonTest.get())
            }

            if (enableAndroidBuild) {
                androidMain {
                    dependsOn(mobileMain)
                }

                androidMain.dependencies {
                    implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-recovery-blockstore"))
                    implementation(identityLibs.ktor.client.android)
                    implementation(identityLibs.androidx.fragment)
                    implementation(identityLibs.androidx.biometric)
                    implementation(identityLibs.cryptography.provider.jdk)
                    implementation(identityLibs.bouncycastle.prov)
                }

                getByName("androidHostTest") {
                    dependsOn(mobileTest)
                    dependencies {
                        implementation(kotlin("test"))
                        implementation(identityLibs.junit)
                        implementation(identityLibs.robolectric)
                        implementation(identityLibs.kotlinx.coroutines.test)
                    }
                }

                getByName("androidDeviceTest").dependencies {
                    implementation(kotlin("test"))
                    implementation(identityLibs.kotlinx.coroutines.test)
                    implementation(identityLibs.androidx.test.ext.junit)
                    implementation(identityLibs.androidx.test.runner)
                }
            }

            if (enableIosBuild) {
                iosMain {
                    dependsOn(mobileMain)
                }

                iosMain.dependencies {
                    implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-recovery-keychain"))
                    implementation(identityLibs.ktor.client.darwin)
                }

                iosTest {
                    dependsOn(mobileTest)
                }
            }
        }

        if (enableWalletDemoComposeWeb) {
            getByName("wasmJsMain").dependencies {
                implementation(identityLibs.ktor.client.js)
                implementation(identityLibs.kotlinx.browser)
            }
            getByName("wasmJsTest").dependencies {
                implementation(identityLibs.ktor.client.mock)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (name == "testAndroidHostTest") {
        useJUnit()
        systemProperty(
            "walletDemoImageFixturesDir",
            layout.projectDirectory.dir("../../waltid-wallet-demo-test-fixtures/resources/files").asFile.absolutePath,
        )
    }
}

if (enableMobileWallet) {
    val fixture = layout.projectDirectory.dir("../../waltid-wallet-demo-test-fixtures/key-attestation")
    extra["demoKeyAttestationFixture"] = fixture
    apply(from = fixture.file("sources.gradle.kts"))
    val demoKeyAttestationSources = extra["demoKeyAttestationSources"]
    kotlin.sourceSets.named("mobileMain") { kotlin.srcDirs(demoKeyAttestationSources) }
}
