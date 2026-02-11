plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.wallet"

object Versions {
    const val KTOR_VERSION = "3.3.3"
}

kotlin {
    sourceSets {

        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            implementation(identityLibs.oshai.kotlinlogging)

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            implementation(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(project(":waltid-libraries:waltid-did"))

            implementation(project(":waltid-libraries:protocols:waltid-openid4vc"))
            implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
            implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))

            // Ktor client
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Bouncy Castle
            implementation("org.bouncycastle:bcprov-lts8on:2.73.8")
            implementation("org.bouncycastle:bcpkix-lts8on:2.73.8")

            // Problematic libraries:
            implementation("com.nimbusds:nimbus-jose-jwt:10.6")
            implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
        }
        jvmMain.dependencies {
            // Ktor client
            implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmTest.dependencies {
            implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
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
