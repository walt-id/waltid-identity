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
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
    }
}
