plugins {
    id("waltid.multiplatform.library.common")

    id("love.forte.plugin.suspend-transform")
}

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableIosBuild = getSetting("enableIosBuild")

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

// iOS test binaries cannot link without CocoaPods framework paths (JOSESwift from waltid-target-ios).
// iOS source compilation still runs, verifying correctness; only test linking/execution is disabled.
if (enableIosBuild) {
    tasks.matching { it.name.startsWith("linkDebugTestIos") || it.name.startsWith("linkReleaseTestIos") }.configureEach {
        enabled = false
    }
}

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }
}
