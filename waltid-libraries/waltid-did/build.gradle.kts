import love.forte.plugin.suspendtrans.configuration.ClassInfo
import love.forte.plugin.suspendtrans.configuration.SuspendTransformConfiguration
import love.forte.plugin.suspendtrans.configuration.TargetPlatform
import love.forte.plugin.suspendtrans.gradle.SuspendTransPluginConstants
import love.forte.plugin.suspendtrans.gradle.SuspendTransformPluginExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
    id("love.forte.plugin.suspend-transform")
}

group = "id.walt.did"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }

    includeAnnotation = false // Required in the current version to avoid "compileOnly" warning
}


kotlin {
    jvmToolchain(17)
}

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

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
        outputModuleName = "dids"
        /*browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }*/
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    val ktor_version = "3.1.2"
    sourceSets {
        val commonMain by getting {
            dependencies {
                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

                // Ktor client
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-serialization:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("io.ktor:ktor-client-json:$ktor_version")
                implementation("io.ktor:ktor-client-logging:$ktor_version")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

                // Date
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

                // Uuid
                implementation("app.softwork:kotlinx-uuid-core:0.1.4")

                // Crypto
                api(project(":waltid-libraries:crypto:waltid-crypto"))

                // Encodings
                implementation("net.thauvin.erik.urlencoder:urlencoder-lib:1.6.0")

                // Logging
                implementation("io.github.oshai:kotlin-logging:7.0.5")


                // suspend-transform plugin annotations (required in the current version to avoid "compileOnly" warning)
                implementation("${SuspendTransPluginConstants.ANNOTATION_GROUP}:${SuspendTransPluginConstants.ANNOTATION_NAME}:${SuspendTransPluginConstants.ANNOTATION_VERSION}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Ktor client
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")

                // Json canonicalization
                implementation("io.github.erdtman:java-json-canonicalization:1.1")

                // Multiformat
//                implementation("com.github.multiformats:java-multibase:v1.1.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.slf4j:slf4j-simple:2.0.16")


                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
                implementation("io.ktor:ktor-server-test-host:$ktor_version")
                implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-server-netty:$ktor_version")
                implementation("io.ktor:ktor-network-tls-certificates:${ktor_version}")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:$ktor_version")

                implementation(npm("canonicalize", "2.0.0"))
                implementation(npm("uuid", "9.0.1"))
            }
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id DID library")
                description.set("walt.id Kotlin/Java library working with Decentralised Identifiers (DIDs)")
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
            url = uri(if (version.toString().endsWith("SNAPSHOT")) uri("https://maven.waltid.dev/snapshots") else uri("https://maven.waltid.dev/releases"))
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: File("$rootDir/secret_maven_username.txt").let { if (it.isFile) it.readLines().first() else "" }
                password = System.getenv("MAVEN_PASSWORD") ?: File("$rootDir/secret_maven_password.txt").let { if (it.isFile) it.readLines().first() else "" }
            }
        }
    }
}
