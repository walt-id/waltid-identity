plugins {
    id("waltid.mobile.library")
    alias(identityLibs.plugins.sqldelight)
}

group = "id.walt.protocols"

kotlin {
    androidLibrary {
        namespace = "id.walt.wallet2.persistence"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:waltid-did"))
            implementation(identityLibs.sqldelight.runtime)
            implementation(identityLibs.sqldelight.coroutines)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        if (providers.gradleProperty("enableAndroidBuild").orNull.toBoolean()) {
            androidMain.dependencies {
                implementation(identityLibs.sqldelight.android.driver)
                api(project(":waltid-libraries:crypto:waltid-crypto-android"))
            }
        }
        if (providers.gradleProperty("enableIosBuild").orNull.toBoolean()) {
            iosMain.dependencies {
                api(project(":waltid-libraries:crypto:waltid-crypto-ios"))
                implementation(identityLibs.sqldelight.native.driver)
            }
        }
    }
}

sqldelight {
    databases {
        create("WalletPersistenceDatabase") {
            packageName.set("id.walt.wallet2.persistence.db")
        }
    }
}
