plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.protocols"

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.ktor.client.core)
            implementation(identityLibs.ktor.client.content.negotiation)
            implementation(identityLibs.ktor.serialization.kotlinx.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(identityLibs.ktor.client.mock)
        }

        if (providers.gradleProperty("enableIosBuild").orNull.toBoolean()) {
            val iosArm64Main by getting {
                dependencies {
                    implementation(identityLibs.ktor.client.darwin)
                }
            }
            val iosSimulatorArm64Main by getting {
                dependencies {
                    implementation(identityLibs.ktor.client.darwin)
                }
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Wallet SDK - Client library")
        description.set(
            "Shared Kotlin Multiplatform client-side orchestration for native wallet " +
                "applications using the walt.id OpenID4VC wallet core."
        )
    }
}
