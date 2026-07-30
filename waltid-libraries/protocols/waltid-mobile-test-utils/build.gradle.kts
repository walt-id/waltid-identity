plugins {
    id("waltid.mobile.library")
}

group = "id.walt.protocols"

waltidMobile {
    androidNamespace.set("id.walt.mobile.test")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:protocols:waltid-openid4vci"))
            implementation(project(":waltid-libraries:protocols:waltid-openid4vci-wallet"))
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
