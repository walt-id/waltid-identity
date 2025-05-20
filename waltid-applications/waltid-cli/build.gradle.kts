@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

object Versions {
    const val KTOR_VERSION = "3.1.2"
}

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt.cli"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain(17)

//    mingwX64("windows")
//    linuxX64("linux")
    macosX64("macos") {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

//    targets.configureEach {
//        compilations.configureEach {
//            compileTaskProvider.configure {
//                compilerOptions {
//                    freeCompilerArgs.add("-Xexpect-actual-classes")
//                }
//            }
//        }
//    }

    compilerOptions {
        // Compilation warning on expect/actual classes
        // https://youtrack.jetbrains.com/issue/KT-61573
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

//    targets.all {
//        compilations.all {
//            kotlinOptions {
//                // Compilation warning on expect/actual classes
//                // https://youtrack.jetbrains.com/issue/KT-61573
//                freeCompilerArgs += "-Xexpect-actual-classes"
//            }
//        }
//    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_15 // JVM got Ed25519 at version 15
                }
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        binaries {
            executable {
                // Define the main class for the application.
                // Works with:
                //     ../../gradlew run --args="--help"
                mainClass = "id.walt.cli.MainKt"
                applicationName = "waltid"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":waltid-libraries:crypto:waltid-crypto"))
                api(project(":waltid-libraries:waltid-did"))
                api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
                api(project(":waltid-libraries:credentials:waltid-verification-policies"))
                api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
                api(project(":waltid-libraries:protocols:waltid-openid4vc"))

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

                // CLI
                implementation("com.github.ajalt.clikt:clikt:5.0.3")
                implementation("com.github.ajalt.clikt:clikt-markdown:5.0.3")
                implementation("com.github.ajalt.mordant:mordant:3.0.2")
                implementation("com.github.ajalt.mordant:mordant-markdown:3.0.2")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

                // Logging
                implementation("io.github.oshai:kotlin-logging:7.0.5")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }

        val macosTest by getting {
            dependsOn(commonTest)
        }

        val jvmMain by getting {
            dependencies {
                // https://mvnrepository.com/artifact/com.github.ajalt.clikt/clikt-jvm
                runtimeOnly("com.github.ajalt.clikt:clikt-jvm:5.0.3")

                implementation("com.google.code.gson:gson:2.12.1")

                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.16")

                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")

                // BouncyCastle for PEM import
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.7")

                // JSON
                implementation("org.json:json:20240303")

            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("com.wolpl.clikt-testkit:clikt-testkit:3.0.0")

                implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")

                // Ktor server
                implementation("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-netty-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-sessions-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-auth-jwt-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-auto-head-response-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-double-receive-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-host-common-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-compression-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-cors-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-forwarded-header-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-call-logging-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-call-id-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")

                // Ktor client
                implementation("io.ktor:ktor-client-core-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-serialization-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-json-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
                implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")

            }
        }
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    kotlinOptions {
//        allWarningsAsErrors = false // default
//        freeCompilerArgs += listOf("-Xreport-perf", "-Xno-param-assertions")
//        // To suppress specific warnings (e.g., deprecations)
//        // freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
//    }
//}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-nowarn")
}

/*tasks.test {
    useJUnitPlatform()
}*/
