plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}
group = "id.walt.credentials"
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
            api(project(":waltid-libraries:crypto:waltid-cose"))
            api(identityLibs.kotlinx.io.bytestring)
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.cbor)
        }
    }
}
