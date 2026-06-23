plugins {
    id("waltid.mobile.library")
    alias(identityLibs.plugins.sqldelight)
}

group = "id.walt.protocols"

kotlin {
    android {
        namespace = "id.walt.wallet2.persistence"

        if (enableAndroidBuild) {
            withHostTestBuilder {}
        }
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
        if (enableAndroidBuild) {
            androidMain.dependencies {
                implementation(identityLibs.sqldelight.android.driver)
                api(project(":waltid-libraries:crypto:waltid-crypto-android"))
            }
        }
        if (enableIosBuild) {
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
