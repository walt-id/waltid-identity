plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(identityLibs.kotlinx.io.bytestring)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id ISO mdoc Reader Trust")
        description.set("Transport-independent ISO mdoc reader authentication trust evaluation")
    }
}
