plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.wallet"


kotlin {
    sourceSets {

        commonMain.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.datetime)

            implementation(identityLibs.oshai.kotlinlogging)

            implementation(identityLibs.kotlinx.coroutines.core)

            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:waltid-did"))

            implementation(project(":waltid-libraries:protocols:waltid-openid4vc"))
            implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Bouncy Castle
            implementation(identityLibs.bouncycastle.prov)
            implementation(identityLibs.bouncycastle.pkix)

            // Problematic libraries:
            implementation(identityLibs.nimbus.jose.jwt)
            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
        }
        jvmMain.dependencies {
            // Ktor client
            implementation(identityLibs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmTest.dependencies {
            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id wallet core")
        description.set("walt.id Kotlin/Java wallet core library")
    }
}
