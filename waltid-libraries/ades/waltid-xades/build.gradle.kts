plugins {
    id("waltid.multiplatform.library.jvm")
    id("waltid.publish.maven")
}

group = "id.walt.ades"

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:ades:waltid-ades-core"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.oshai.kotlinlogging)
        }
        jvmMain.dependencies {
            implementation(identityLibs.santuario.xmlsec)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(project(":waltid-libraries:ades:waltid-ades-test"))
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.bouncycastle.prov)
            implementation(identityLibs.bouncycastle.pkix)
            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.junit.jupiter.engine)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id XAdES")
        description.set("walt.id Kotlin library for XAdES (XML AdES, ETSI EN 319 132-1) signatures (JVM)")
    }
}
