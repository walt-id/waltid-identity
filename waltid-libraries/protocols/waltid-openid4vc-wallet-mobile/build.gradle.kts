@file:OptIn(ExperimentalAbiValidation::class)

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.util.zip.ZipFile

plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
    alias(identityLibs.plugins.skie)
}

group = "id.walt.protocols"

waltidMobile {
    androidNamespace.set("id.walt.wallet2.mobile")
}

skie {
    analytics {
        enabled.set(false)
    }

    build {
        produceDistributableFramework()
    }
}

kotlin {
    explicitApi()

    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    if (enableIosBuild) {
        val walletCoreXcFramework = XCFramework("WalletCore")

        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
            binaries.framework {
                baseName = "WalletCore"
                isStatic = true
                binaryOption("bundleId", "id.walt.wallet.core")
                walletCoreXcFramework.add(this)
            }

        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
            implementation(project(":waltid-libraries:protocols:waltid-18013-7-verifier"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.ktor.client.mock)
            implementation(identityLibs.ktor.client.content.negotiation)
            implementation(identityLibs.ktor.serialization.kotlinx.json)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        if (enableAndroidBuild) {
            androidMain.dependencies {
                implementation(identityLibs.ktor.client.android)
                implementation(identityLibs.androidx.credentials.registry.mdoc)
                implementation(identityLibs.androidx.credentials.registry.openid)
                implementation(identityLibs.androidx.credentials.registry.sdjwtvc)
                implementation(identityLibs.androidx.credentials.registry.provider)
                implementation(identityLibs.androidx.credentials.registry.provider.play.services)
                // The ISO 18013-7 Annex C matcher is a vendored WASM binary, not a dependency:
                // ANNEX-C-MATCHER.md records its origin and how to refresh it.
            }
            val androidHostTest by getting {
                dependencies {
                    implementation(kotlin("test"))
                    implementation(identityLibs.junit)
                    implementation(identityLibs.robolectric)
                }
            }
            named("androidHostTest") {
                dependencies {
                    implementation(identityLibs.sqldelight.sqlite.driver)
                }
            }
        }
        if (enableIosBuild) {
            iosMain.dependencies {
                implementation(identityLibs.ktor.client.darwin)
                implementation(identityLibs.signum.indispensable)
                implementation(identityLibs.signum.supreme)
            }
        }
        if (enableAndroidBuild) {
            val androidDeviceTest by getting {
                dependencies {
                    implementation(kotlin("test"))
                    implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
                    implementation(identityLibs.kotlinx.coroutines.test)
                    implementation(identityLibs.androidx.test.runner)
                    implementation(identityLibs.androidx.test.ext.junit)
                    implementation(identityLibs.ktor.client.android)
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

// Configured through the typed extension rather than the `android`/`androidComponents` script
// accessors, because those are generated only when the Android plugin is applied, and this module is
// also built with `enableAndroidBuild=false` for iOS.
if (enableAndroidBuild) extensions.configure<KotlinMultiplatformAndroidComponentsExtension>("androidComponents") {
    // Asset and resource processing is off by default for Android KMP libraries, and required for
    // `androidMain/assets` to be packaged into the AAR at all. See the matcher provenance docs.
    finalizeDsl { android ->
        android.androidResources.enable = true
    }

    // The host tests read the matcher through the asset API, which still passes when the published AAR
    // carries no assets at all, so assert on the artifact itself.
    onVariants { variant ->
        val aar = variant.artifacts.get(SingleArtifact.AAR)
        val verifyMatcherPackaging = tasks.register("verifyMatcherPackaging") {
            description = "Fails if the AAR does not carry the vendored matcher assets and notices."
            inputs.file(aar).withPropertyName("aar")
            doLast {
                val entries = ZipFile(aar.get().asFile).use { zip ->
                    zip.entries().asSequence().map { it.name }.toSet()
                }
                val missing = setOf(
                    "assets/id/walt/wallet2/mobile/identitycredentialmatcher.wasm",
                    "assets/id/walt/wallet2/mobile/NOTICE-identitycredentialmatcher.txt",
                    "assets/id/walt/wallet2/mobile/issuance.wasm",
                    "assets/id/walt/wallet2/mobile/NOTICE-issuance.txt",
                ) - entries
                require(missing.isEmpty()) { "AAR is missing vendored matcher assets: $missing" }
            }
        }
        tasks.named("check") { dependsOn(verifyMatcherPackaging) }
    }
}
