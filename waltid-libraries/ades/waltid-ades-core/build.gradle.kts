plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.ades"

kotlin {
    js {
        outputModuleName = "ades-core"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            api(identityLibs.kotlinx.serialization.json)
            api(identityLibs.ktor.client.core)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.mock)
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
        name.set("walt.id AdES core")
        description.set("walt.id Kotlin multiplatform building blocks for AdES signatures (ETSI EN 319 102-1): ASN.1, X.509, CRL, OCSP, RFC 3161 time-stamps and validation")
    }
}
