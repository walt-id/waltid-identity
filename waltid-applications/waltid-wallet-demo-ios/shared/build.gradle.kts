plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    kotlin("plugin.serialization")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Shared WAL-1033 native wallet demo bridge"
        homepage = "https://walt.id"
        version = "1.0"
        ios.deploymentTarget = "15.4"
        framework {
            baseName = "shared"
            isStatic = true
        }
        extraSpecAttributes["libraries"] = "'c++', 'sqlite3'"
    }

    sourceSets {
        commonMain.dependencies {
            // Export wallet-client API so tests can use the real SDK
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-client"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }

        iosMain.dependencies {
            implementation(identityLibs.ktor.client.darwin)
        }
    }
}
