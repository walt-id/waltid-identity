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
            implementation(identityLibs.kotlinx.coroutines.core)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            /*
             * walt.id:
             */
            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
            implementation(project(":waltid-libraries:credentials:waltid-dcql"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Relying Party Registration Certificate library")
        description.set("Creation, signing, parsing and wallet-side verification of EUDI Wallet-Relying Party Registration Certificates (WRPRC / rc-wrp+jwt) as specified in ETSI TS 119 475.")
    }
}