plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    )
        .forEach {
            val iosUtilsProjectName = "crypto-ios-utils"
            val target = it.name

            val sdk = when (target) {
                "iosArm64" -> "iphoneos"
                else -> "iphonesimulator"
            }

            it.compilations.getByName("main") {
                cinterops.create("waltid-crypto-ios") {
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
        ios.deploymentTarget = "15.4"
        framework {
            baseName = "waltid-crypto-ios"
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
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            this.dependsOn(commonMain.get())
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                api(project(":waltid-libraries:waltid-crypto"))
            }
        }
    }
}


task("testClasses")