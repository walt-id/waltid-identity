import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
    id("waltid.multiplatform.library.common")

    id("love.forte.plugin.suspend-transform")
}

kotlin {
    jvm()

    js(IR) {
        useCommonJs()
        generateTypeScriptDefinitions()
        binaries.library()

        nodejs {
            testTask {
                failOnNoDiscoveredTests = false
                useMocha { timeout = "30s" }
            }
        }
        browser {
            testTask { enabled = false }
        }
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }
}

if (enableIosBuild) {
    kotlin.targets.withType<KotlinNativeTarget>().configureEach {
        val sdk = when (name) {
            "iosArm64" -> "iphoneos"
            else -> "iphonesimulator"
        }

        binaries.withType<TestExecutable>().configureEach {
            val targetIosProject = project(":waltid-libraries:crypto:waltid-target-ios")
            val frameworkPath = targetIosProject.layout.buildDirectory
                .dir("cocoapods/synthetic/ios/build/Debug-$sdk/JOSESwift")
                .get()
                .asFile
                .absolutePath

            linkerOpts("-F$frameworkPath", "-framework", "JOSESwift", "-rpath", frameworkPath, "-lsqlite3")
        }
    }
}

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }
}
