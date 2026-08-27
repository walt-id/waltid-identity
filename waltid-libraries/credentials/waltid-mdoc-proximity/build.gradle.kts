plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            api(project(":waltid-libraries:crypto:waltid-cose"))
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
            implementation(identityLibs.cryptography.provider.optimal)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.kotlinx.serialization.cbor)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlincrypto.hash.sha2)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id ISO mdoc Proximity")
        description.set("Radio-independent ISO mdoc proximity holder protocol engine")
    }
}
