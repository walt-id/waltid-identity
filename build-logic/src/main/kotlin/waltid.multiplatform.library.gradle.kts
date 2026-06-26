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

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }
}
