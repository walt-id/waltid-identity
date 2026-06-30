plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    kotlin("plugin.serialization")
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
            baseName = "shared"
            isStatic = true
        }

        pod("JOSESwift") {
            version = "3.0.0"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            implementation(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:protocols:waltid-openid4vc"))
            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))

            implementation(identityLibs.kotlinx.serialization.json)

            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.ktor.client.serialization)
            implementation(identityLibs.ktor.client.content.negotiation)
            implementation(identityLibs.ktor.serialization.kotlinx.json)
            implementation(identityLibs.ktor.client.json)
            implementation(identityLibs.ktor.client.logging)
        }
        commonTest.dependencies {

        }

        iosMain.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-crypto-ios"))
            implementation("io.ktor:ktor-client-darwin:3.5.1")
        }
    }
}
