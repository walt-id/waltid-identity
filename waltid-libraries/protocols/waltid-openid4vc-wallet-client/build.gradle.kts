plugins {
    id("waltid.mobile.library")
}

group = "id.walt.protocols"

kotlin {
    androidLibrary {
        namespace = "id.walt.wallet2.client"

        withDeviceTestBuilder {
            sourceSetTreeName = "androidDeviceTest"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            packaging {
                resources.excludes.add("META-INF/DEPENDENCIES")
                resources.excludes.add("META-INF/LICENSE.md")
                resources.excludes.add("META-INF/NOTICE.md")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-client"))
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(identityLibs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(identityLibs.ktor.client.darwin)
        }
        iosTest.dependencies {
            implementation(identityLibs.ktor.client.darwin)
        }
        val androidDeviceTest by getting {
            dependsOn(commonTest.get())
            dependencies {
                implementation(kotlin("test"))
                implementation(identityLibs.kotlinx.coroutines.test)
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test.ext:junit:1.2.1")
            }
        }
    }
}

configurations.all {
    if (name.contains("android", ignoreCase = true)) {
        exclude(group = "io.ktor", module = "ktor-client-java")
    }
}

// Add JOSESwift framework path and SQLite for iOS test binaries
// This must be configured after evaluation to ensure the framework is built
afterEvaluate {
    kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
            val cryptoIosProject = project(":waltid-libraries:crypto:waltid-crypto-ios")
            // Framework is at: build/cocoapods/synthetic/ios/build/Debug-iphonesimulator/JOSESwift/JOSESwift.framework
            // Need to point -F to the directory containing JOSESwift.framework
            val frameworkPath = cryptoIosProject.layout.buildDirectory.dir("cocoapods/synthetic/ios/build/Debug-iphonesimulator/JOSESwift").get().asFile.absolutePath
            println("Configuring iOS test binary with JOSESwift framework at: $frameworkPath")
            linkerOpts("-F$frameworkPath", "-framework", "JOSESwift")
            // Link SQLite (system framework needed by SQLDelight)
            linkerOpts("-lsqlite3")
            // Add runtime search path so the framework can be found at runtime
            linkerOpts("-rpath", frameworkPath)
        }
    }
}
