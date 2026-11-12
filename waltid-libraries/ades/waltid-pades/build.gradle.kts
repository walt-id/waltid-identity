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
            implementation(identityLibs.pdfbox)
            implementation(identityLibs.bouncycastle.prov)
            implementation(identityLibs.bouncycastle.pkix)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(project(":waltid-libraries:ades:waltid-ades-test"))
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.junit.jupiter.engine)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id PAdES")
        description.set("walt.id Kotlin library for PAdES (PDF AdES, ETSI EN 319 142-1) signatures (JVM)")
    }
}
