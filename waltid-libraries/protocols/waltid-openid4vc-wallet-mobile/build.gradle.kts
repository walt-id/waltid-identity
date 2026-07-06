plugins {
    id("waltid.mobile.library")
    id("waltid.mobile.sdk.documentation")
}

group = "id.walt.protocols"

waltidMobile {
    androidNamespace.set("id.walt.wallet2.mobile")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence-mobile"))
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
        if (enableAndroidBuild) {
            androidMain.dependencies {
                implementation(identityLibs.ktor.client.android)
            }
        }
        if (enableIosBuild) {
            iosMain.dependencies {
                implementation(identityLibs.ktor.client.darwin)
            }
        }
        if (enableAndroidBuild) {
            val androidDeviceTest by getting {
                dependencies {
                    implementation(kotlin("test"))
                    implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
                    implementation(identityLibs.kotlinx.coroutines.test)
                    implementation(identityLibs.androidx.test.runner)
                    implementation(identityLibs.androidx.test.ext.junit)
                    implementation(identityLibs.ktor.client.android)
                }
            }
        }
    }
}
