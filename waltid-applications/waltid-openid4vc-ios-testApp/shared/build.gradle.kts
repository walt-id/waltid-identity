plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    kotlin("plugin.serialization")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    object Versions {
        const val KTOR_VERSION = "3.3.3"
    }

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
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

            implementation("io.ktor:ktor-client-core:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-serialization:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-json:${Versions.KTOR_VERSION}")
            implementation("io.ktor:ktor-client-logging:${Versions.KTOR_VERSION}")
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
                implementation(project(":waltid-libraries:crypto:waltid-crypto-ios"))
                implementation("io.ktor:ktor-client-darwin:3.2.3")
            }
        }
    }
}
