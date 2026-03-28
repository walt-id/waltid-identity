plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.mdoc-credentials"

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.kotlincrypto.hash.sha2)
            implementation(identityLibs.kotlincrypto.macs.hmac.sha2)
            implementation(identityLibs.kotlincrypto.random)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
        }
        jvmTest.dependencies {
            implementation(identityLibs.bouncycastle.prov)
            implementation(identityLibs.bouncycastle.pkix)
            implementation("io.mockk:mockk:1.13.16")

            implementation(kotlin("reflect"))

            //Interoperability test support with A-SIT's implementation
            // Temporarily disabled, see waltid-libraries/credentials/waltid-mdoc-credentials/src/jvmTest/kotlin/interop/ASITTest.kt
            //implementation("at.asitplus.wallet:vck:5.8.0")
            //implementation("at.asitplus.wallet:mobiledrivinglicence:1.2.0")
        }
        jsMain.dependencies {
            implementation(npm("cose-js", "0.9.0"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id mdoc credentials")
        description.set("walt.id Kotlin/Java library for ISO mdoc/mDL 18013-5")
    }
}
