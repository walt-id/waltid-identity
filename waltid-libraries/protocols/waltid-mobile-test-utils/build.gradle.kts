plugins {
    id("waltid.mobile.library")
}

group = "id.walt.protocols"

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
    }
}
