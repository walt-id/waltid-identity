plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.ades"

kotlin {
    js {
        outputModuleName = "cbades"
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
            implementation(identityLibs.ktor.client.java)
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.bouncycastle.prov)
            implementation(identityLibs.bouncycastle.pkix)
            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.junit.jupiter.engine)
        }
    }
}

tasks.named<Test>("jvmTest") {
    // Opt-in interoperability run against plugtest material: -PcbadesInteropDir=/path/to/portal/all
    providers.gradleProperty("cbadesInteropDir").orNull?.let { systemProperty("cbades.interop.dir", it) }
}

mavenPublishing {
    pom {
        name.set("walt.id CB-AdES")
        description.set("walt.id Kotlin multiplatform library for CB-AdES (CBOR AdES, ETSI TS 119 152-1) signatures")
    }
}
