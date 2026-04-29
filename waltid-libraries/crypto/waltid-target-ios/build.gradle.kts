plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
}

kotlin {
    applyDefaultHierarchyTemplate()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    )
        .forEach {
            val iosUtilsProjectName = "implementation"
            val target = it.name

            val sdk = when (target) {
                "iosArm64" -> "iphoneos"
                else -> "iphonesimulator"
            }

            it.compilations.getByName("main") {
                cinterops.create("implementation") {
                    val interopTask = tasks[interopProcessingTaskName]
                    interopTask.dependsOn(":waltid-libraries:crypto:${project.name}:$iosUtilsProjectName:$iosUtilsProjectName-$target")
                    headers("$projectDir/$iosUtilsProjectName/build/$target/Release-$sdk/include/waltid_crypto_ios_utils/waltid_crypto_ios_utils-Swift.h")
                }
            }
        }

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "16.0"
        framework {
            baseName = "waltid-target-ios"
            isStatic = true
        }

        pod("JOSESwift"){
            version = "3.0.0"
        }
    }

    sourceSets {

        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
            languageSettings.optIn("kotlin.io.encoding.ExperimentalEncodingApi")
        }

        iosMain.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
    }
}
