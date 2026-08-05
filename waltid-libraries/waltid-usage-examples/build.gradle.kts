plugins {
    id("waltid.full.library")
}

group = "id.walt.examples"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Brings waltid-did and waltid-sdjwt along as api dependencies, so a single dependency covers every
            // library a consumer needs for DID-based issuance and verification.
            api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}

// The usage examples report a per-platform support table, which is only useful if it reaches the console.
tasks.withType<AbstractTestTask>().configureEach {
    testLogging.showStandardStreams = true
}
