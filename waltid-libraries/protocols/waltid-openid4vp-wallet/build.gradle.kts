plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.protocols"

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Kotlinx
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
            implementation("app.softwork:kotlinx-uuid-core:0.1.7")

            // JSON
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

            implementation("io.github.optimumcode:json-schema-validator:0.5.3")
            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

            // CBOR
            implementation(identityLibs.kotlinx.serialization.cbor)

            /*
             * walt.id:
             */
            implementation(project(":waltid-libraries:protocols:waltid-openid4vp"))
            implementation(project(":waltid-libraries:protocols:waltid-openid4vp-clientidprefix"))
            implementation(project(":waltid-libraries:credentials:waltid-dcql"))
            implementation(project(":waltid-libraries:credentials:waltid-verification-policies2"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            implementation(project(":waltid-libraries:web:waltid-ktor-notifications-core"))
            implementation(project(":waltid-libraries:credentials:waltid-holder-policies"))
        }
        commonTest.dependencies {
            implementation(identityLibs.bundles.waltid.ktortesting)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Wallet SDK - OpenID4VP version")
        description.set("walt.id Kotlin/Java Verifier for OpenID4VP")
    }
}
