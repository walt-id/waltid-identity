plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.credentials"

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        commonMain.dependencies {
            // Trust vocabulary (TrustDecision, TrustedEntityType, ...) is serializable public API
            api(identityLibs.kotlinx.serialization.json)

            // Static adapter: PKIX validation against configured anchors
            api(project(":waltid-libraries:crypto:waltid-x509"))

            // HTTP adapter: standard outbound client with the private-network guard
            implementation(project(":waltid-libraries:web:waltid-web-data-fetching"))
            implementation(identityLibs.bundles.waltid.ktor.client)

            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Trust Decision")
        description.set("Trust decision vocabulary, evaluator port and static/HTTP adapters")
    }
}
