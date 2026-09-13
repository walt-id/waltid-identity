@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
}

group = "id.walt.credentials"

waltidMobile {
    androidNamespace.set("id.walt.mdoc.proximity.mobile")
}

kotlin {
    explicitApi()

    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:credentials:waltid-mdoc-proximity"))
            implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
            implementation(identityLibs.kotlinx.atomicfu)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        if (enableAndroidBuild) {
            val androidHostTest by getting {
                dependencies {
                    implementation(kotlin("test"))
                    implementation(identityLibs.kotlinx.coroutines.test)
                    implementation(identityLibs.junit)
                    implementation(identityLibs.robolectric)
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") useJUnit()
}

// Native OS doubles are linked only into the simulator test executable, never a library/framework.
if (enableIosBuild) {
    val testObjc = layout.projectDirectory.dir("src/iosSimulatorArm64Test/objc")
    val testObject = layout.buildDirectory.file("core-bluetooth-test-doubles/CoreBluetoothTestDoubles.o")
    val developerDirectory = providers.exec { commandLine("xcode-select", "-p") }
        .standardOutput.asText.map { it.trim() }
    val compileCoreBluetoothTestDoubles by tasks.registering(Exec::class) {
        inputs.files(testObjc.file("CoreBluetoothTestDoubles.h"), testObjc.file("CoreBluetoothTestDoubles.m"))
        outputs.file(testObject)
        val output = testObject.get().asFile
        doFirst { output.parentFile.mkdirs() }
        commandLine("/usr/bin/clang", "-target", "arm64-apple-ios16.0-simulator", "-isysroot",
            "${developerDirectory.get()}/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk",
            "-fobjc-arc", "-c", testObjc.file("CoreBluetoothTestDoubles.m").asFile, "-o", output)
    }
    kotlin.targets.named<KotlinNativeTarget>("iosSimulatorArm64") {
        compilations.getByName("test").cinterops.create("coreBluetoothTestDoubles") {
            definitionFile.set(testObjc.file("CoreBluetoothTestDoubles.def"))
            includeDirs(testObjc.asFile)
        }
        binaries.withType<TestExecutable>().configureEach {
            linkerOpts(testObject.get().asFile.absolutePath)
            linkTaskProvider.configure {
                dependsOn(compileCoreBluetoothTestDoubles)
                inputs.file(testObject)
            }
        }
    }
}
