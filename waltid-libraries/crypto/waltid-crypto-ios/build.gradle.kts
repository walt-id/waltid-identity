plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.github.ben-manes.versions")
}

kotlin {

    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "15.4"

        framework {
            baseName = "waltid-crypto-ios"
            isStatic = true
            export(project(":waltid-libraries:crypto:waltid-target-ios"))
            export(project(":waltid-libraries:crypto:waltid-crypto"))
        }

        pod("JOSESwift") {
            version = "3.0.0"
        }
    }

    sourceSets {

        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            this.dependsOn(commonMain.get())
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                api(project(":waltid-libraries:crypto:waltid-crypto"))
                api(project(":waltid-libraries:crypto:waltid-target-ios"))
        }
    }
}


task("testClasses")
