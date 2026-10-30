plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.ades"

kotlin {
    js {
        outputModuleName = "jades"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:ades:waltid-ades-core"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.oshai.kotlinlogging)
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
        name.set("walt.id JAdES")
        description.set("walt.id Kotlin multiplatform library for JAdES (JSON AdES, ETSI TS 119 182-1) signatures")
    }
}
