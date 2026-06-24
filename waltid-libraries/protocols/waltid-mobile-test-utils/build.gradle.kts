plugins {
    id("waltid.mobile.library")
}

group = "id.walt.protocols"

kotlin {
    android {
        namespace = "id.walt.mobile.test"

        withHostTestBuilder {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
    }
}
