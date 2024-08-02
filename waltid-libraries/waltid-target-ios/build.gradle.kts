plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    kotlin("plugin.serialization")
}

kotlin {

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
                    interopTask.dependsOn(":waltid-libraries:${project.name}:$iosUtilsProjectName:$iosUtilsProjectName-$target")
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
            version = "2.4.0"
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

        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            this.dependsOn(commonMain)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}