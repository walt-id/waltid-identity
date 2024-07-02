plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
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
            version = "2.4.0"
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(project(":waltid-libraries:waltid-sdjwt"))
            implementation(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:waltid-openid4vc"))
            implementation(project(":waltid-libraries:waltid-verifiable-credentials"))
        }
        commonTest.dependencies {

        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            this.dependsOn(commonMain.get())
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation(project(":waltid-libraries:waltid-crypto-ios"))
                implementation("io.ktor:ktor-client-darwin:2.3.12")
            }
        }
    }
}