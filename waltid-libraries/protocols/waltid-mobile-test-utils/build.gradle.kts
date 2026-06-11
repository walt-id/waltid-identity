plugins {
    id("waltid.mobile.library")
}

group = "id.walt.protocols"

kotlin {
    androidLibrary {
        namespace = "id.walt.mobile.test"

        withDeviceTestBuilder {
            sourceSetTreeName = "androidDeviceTest"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(identityLibs.ktor.client.android)
        }

        iosMain.dependencies {
            implementation(identityLibs.ktor.client.darwin)
        }

        val androidDeviceTest by getting {
            dependsOn(commonTest.get())
            dependencies {
                implementation(kotlin("test"))
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test.ext:junit:1.2.1")
            }
        }
    }
}

// Exclude JVM-only ktor engines from Android device test runtime
configurations.configureEach {
    if (name.contains("androidDeviceTest") && name.contains("RuntimeClasspath")) {
        exclude(group = "io.ktor", module = "ktor-client-java")
        exclude(group = "io.ktor", module = "ktor-client-cio")
        exclude(group = "io.ktor", module = "ktor-client-apache5")
    }
}
