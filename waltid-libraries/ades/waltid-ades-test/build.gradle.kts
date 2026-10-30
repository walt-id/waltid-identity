plugins {
    id("waltid.multiplatform.library.jvm")
}

group = "id.walt.ades"

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:ades:waltid-ades-core"))
        }
        jvmMain.dependencies {
            api(identityLibs.bouncycastle.prov)
            api(identityLibs.bouncycastle.pkix)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
    }
}
