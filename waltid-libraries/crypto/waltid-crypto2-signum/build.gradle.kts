@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

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

// IosSignumKeyBackendTest exercises the real iOS keychain, which is unavailable to a bundle-less executable: a
// Kotlin/Native simulator test binary carries no application-identifier entitlement, so securityd rejects the very
// first SecItemCopyMatching with errSecNotAvailable (-25291, "No keychain is available"). It therefore needs a real
// device or an app host, exactly like AndroidSignumKeyBackendDeviceTest, which CI does not run either. The rest of
// the module (13 tests against a fake backend) keeps running on the simulator.
// Opt in with -PrunIosKeychainTests=true when running against a device or from an app host.
val runIosKeychainTests = providers.gradleProperty("runIosKeychainTests").map(String::toBoolean).getOrElse(false)

if (!runIosKeychainTests) {
    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        filter.excludeTestsMatching("id.walt.crypto2.signum.IosSignumKeyBackendTest")
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2 Signum provider")
        description.set("Android KeyStore and iOS Keychain provider for walt.id crypto2")
    }
}
