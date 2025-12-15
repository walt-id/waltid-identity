plugins {
    id("waltid.multiplatform.library.jvm")
    id("waltid.publish.maven")
}

group = "id.walt.crypto2"


kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(identityLibs.bcprov.lts8on)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.nimbus.jose.jwt)
            implementation(identityLibs.junit.jupiter.api)
            runtimeOnly(identityLibs.junit.jupiter.engine)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2")
        description.set("Next generation cryptography primitives for walt.id")
    }
}
