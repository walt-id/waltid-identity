plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    kotlin("plugin.serialization")
}

kotlin {
    iosArm64()
    iosSimulatorArm64() {
        binaries.all {
            val frameworkDir = "${project.rootDir}/waltid-libraries/crypto/waltid-target-ios/build/cocoapods/synthetic/ios/build/Debug-iphonesimulator/JOSESwift"
            if (file(frameworkDir).exists()) {
                linkerOpts("-F$frameworkDir", "-framework", "JOSESwift", "-rpath", frameworkDir)
            }
        }
    }

    cocoapods {
        summary = "Shared WAL-1033 wallet demo client module"
        homepage = "https://walt.id"
        version = "1.0"
        ios.deploymentTarget = "15.4"
        framework {
            baseName = "shared"
            isStatic = true
        }
        extraSpecAttributes["script_phases"] = """
            [
                {
                    :name => 'Build shared',
                    :execution_position => :before_compile,
                    :shell_path => '/bin/sh',
                    :script => <<-SCRIPT
                        if [ "YES" = "${'$'}OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                            echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \\"YES\\""
                            exit 0
                        fi
                        set -ev
                        REPO_ROOT="${'$'}PODS_TARGET_SRCROOT"
                        "${'$'}REPO_ROOT/../../../gradlew" -p "${'$'}REPO_ROOT" ${'$'}KOTLIN_PROJECT_PATH:syncFramework \
                            -Pkotlin.native.cocoapods.platform=${'$'}PLATFORM_NAME \
                            -Pkotlin.native.cocoapods.archs="${'$'}ARCHS" \
                            -Pkotlin.native.cocoapods.configuration="${'$'}CONFIGURATION" \
                            -PenableIosBuild=true
                    SCRIPT
                }
            ]
        """.trimIndent()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-client"))
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.ktor.client.content.negotiation)
            implementation(identityLibs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            this.dependsOn(commonMain.get())
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation(identityLibs.ktor.client.darwin)
            }
        }
    }
}
