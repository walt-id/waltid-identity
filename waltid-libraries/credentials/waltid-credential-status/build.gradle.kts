plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-credential-key-resolver"))
            implementation(project(":waltid-libraries:credentials:waltid-status-list"))
            implementation(project(":waltid-libraries:crypto:waltid-cose"))
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:web:waltid-web-data-fetching"))
            implementation(identityLibs.bundles.waltid.ktor.client)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.io.bytestring)
            implementation(identityLibs.oshai.kotlinlogging)
            implementation(identityLibs.kotlincrypto.hash.sha2)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.mock)
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
        }
        jvmTest { kotlin.srcDir("src/jvmTestFixtures/kotlin") }
        jvmTest.dependencies {
            implementation(identityLibs.junit.jupiter.params)
            implementation(identityLibs.mockk)
            implementation(identityLibs.ktor.server.test.host)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.server.netty)
            implementation(identityLibs.slf4j.simple)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Credential Status")
        description.set("Authenticated credential-status evaluation shared by wallets and verification policies")
    }
}
