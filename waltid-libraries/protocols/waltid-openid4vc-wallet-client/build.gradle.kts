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
            implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
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
                implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
                implementation(identityLibs.kotlinx.coroutines.test)
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test.ext:junit:1.2.1")
                implementation(identityLibs.ktor.client.android)
            }
        }
    }
}

// Exclude JVM-only ktor engines from Android device test runtime.
// waltid-web-data-fetching's JVM artifact (consumed by Android since no Android target exists)
// includes ktor-client-java/cio/apache5 which fail on Android Runtime (java.net.http.HttpClient unavailable).
configurations.configureEach {
    if (name.contains("androidDeviceTest") && name.contains("RuntimeClasspath")) {
        exclude(group = "io.ktor", module = "ktor-client-java")
        exclude(group = "io.ktor", module = "ktor-client-cio")
        exclude(group = "io.ktor", module = "ktor-client-apache5")
    }
}
