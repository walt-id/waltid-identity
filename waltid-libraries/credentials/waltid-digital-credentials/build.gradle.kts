@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import Versions.KTOR_VERSION
import love.forte.plugin.suspendtrans.gradle.SuspendTransPluginConstants
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.5.3"
    id("love.forte.plugin.suspend-transform")
    id("com.github.ben-manes.versions")
    kotlin("plugin.power-assert")
}

group = "id.walt.credentials"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }

    includeAnnotation = false // Required in the current version to avoid "compileOnly" warning
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

kotlin {
    jvmToolchain(21)
}

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

object Versions {
    const val KTOR_VERSION = "3.2.2"
    const val COROUTINES_VERSION = "1.10.1"
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget = JvmTarget.JVM_15 // JVM got Ed25519 at version 15
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
    js(IR) {
        outputModuleName.set("digital-credentials")
        useEsModules()
//        browser {
//            commonWebpackConfig {
//                cssSupport {
//                    enabled.set(true)
//                }
//            }
//        }
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    //val ktor_version = "3.2.0"
    sourceSets {
        val commonMain by getting {
            dependencies {
//                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.9.0")
//                implementation("io.github.optimumcode:json-schema-validator:0.4.0")
//
//                // Ktor client
                implementation("io.ktor:ktor-client-core:$KTOR_VERSION")
                implementation("io.ktor:ktor-client-serialization:$KTOR_VERSION")
                implementation("io.ktor:ktor-client-content-negotiation:$KTOR_VERSION")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$KTOR_VERSION")
                implementation("io.ktor:ktor-client-json:$KTOR_VERSION")
                implementation("io.ktor:ktor-client-logging:$KTOR_VERSION")
//
//                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
//
//                // Kotlinx
//                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
//                implementation("app.softwork:kotlinx-uuid-core:0.1.6")
//
                // Logging
                implementation("io.github.oshai:kotlin-logging:7.0.5")
//
//                // walt.id
                api(project(":waltid-libraries:crypto:waltid-crypto"))
                api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
                api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
                api(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
                api(project(":waltid-libraries:credentials:waltid-dcql"))
                api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
                api(project(":waltid-libraries:waltid-did"))



                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
                implementation("org.kotlincrypto.hash:sha2")

                // suspend-transform plugin annotations (required in the current version to avoid "compileOnly" warning)
                implementation("${SuspendTransPluginConstants.ANNOTATION_GROUP}:${SuspendTransPluginConstants.ANNOTATION_NAME}:${SuspendTransPluginConstants.ANNOTATION_VERSION}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
//                 Ktor client
//                implementation("io.ktor:ktor-client-okhttp:$ktor_version")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.slf4j:slf4j-simple:2.0.17")
            }
        }
        val jsMain by getting {

        }

        if (enableIosBuild) {
            val iosArm64Main by getting
            val iosSimulatorArm64Main by getting

            val iosMain by creating {
                dependsOn(commonMain)
                iosArm64Main.dependsOn(this)
                iosSimulatorArm64Main.dependsOn(this)
            }

            val iosArm64Test by getting
            val iosSimulatorArm64Test by getting

            val iosTest by creating {
                dependsOn(commonTest)
                iosArm64Test.dependsOn(this)
                iosSimulatorArm64Test.dependsOn(this)
            }
        }
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}

/*extensions.getByType<SuspendTransformGradleExtension>().apply {
    transformers[TargetPlatform.JS] = mutableListOf(
        SuspendTransformConfiguration.jsPromiseTransformer.copy(
            copyAnnotationExcludes = listOf(
                ClassInfo("kotlin.js", "JsExport.Ignore")
            )
        )
    )
}*/

npmPublish {
    registries {
        val envToken = System.getenv("NPM_TOKEN")
        val npmTokenFile = File("secret_npm_token.txt")
        val secretNpmToken = envToken ?: npmTokenFile.let { if (it.isFile) it.readLines().first() else "" }
        val hasNPMToken = secretNpmToken.isNotEmpty()
        val isReleaseBuild = Regex("\\d+.\\d+.\\d+").matches(version.get())
        if (isReleaseBuild && hasNPMToken) {
            readme.set(File("README.md"))
            register("npmjs") {
                uri.set(uri("https://registry.npmjs.org"))
                authToken.set(secretNpmToken)
            }
        }
    }
}

powerAssert {
    functions = listOf("kotlin.assert", "kotlin.test.assertTrue", "kotlin.test.assertEquals", "kotlin.test.assertNull", "kotlin.check")
    includedSourceSets = listOf("commonTest")
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id Digital Credentials")
                description.set("walt.id Kotlin/Java library for Digital Credentials")
                url.set("https://walt.id")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("walt.id")
                        name.set("walt.id")
                        email.set("office@walt.id")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(
                if (version.toString()
                        .endsWith("SNAPSHOT")
                ) uri("https://maven.waltid.dev/snapshots") else uri("https://maven.waltid.dev/releases")
            )
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: File("$rootDir/secret_maven_username.txt").let {
                    if (it.isFile) it.readLines().first() else ""
                }
                password = System.getenv("MAVEN_PASSWORD") ?: File("$rootDir/secret_maven_password.txt").let {
                    if (it.isFile) it.readLines().first() else ""
                }
            }
        }
    }
}
