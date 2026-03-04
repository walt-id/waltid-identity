plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
}

kotlin {
    applyDefaultHierarchyTemplate()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "15.4"
        framework {
            baseName = "waltid-sdjwt-ios"
            isStatic = true
        }

        pod("JOSESwift"){
            version = "3.0.0"
        }
    }

    sourceSets {

        all{
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }

        commonMain.dependencies {
            //put your multiplatform dependencies here
        }
        commonTest.dependencies {
            implementation("io.kotest:kotest-framework-engine:6.1.4")
            implementation("io.kotest:kotest-assertions-core:6.1.4")
        }

        iosMain.dependencies {
            api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto-ios"))
        }
        iosTest.dependencies {}
    }
}
