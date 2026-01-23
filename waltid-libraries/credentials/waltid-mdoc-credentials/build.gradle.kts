plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.mdoc-credentials"

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
        }
        jvmTest.dependencies {
            implementation("org.bouncycastle:bcprov-lts8on:2.73.8")
            implementation("org.bouncycastle:bcpkix-lts8on:2.73.8")
            implementation("io.mockk:mockk:1.13.16")

            implementation(kotlin("reflect"))

            //Interoperability test support with A-SIT's implementation
            implementation("at.asitplus.wallet:vck:5.8.0")
            implementation("at.asitplus.wallet:mobiledrivinglicence:1.2.0")
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
