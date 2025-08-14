import love.forte.plugin.suspendtrans.gradle.SuspendTransPluginConstants
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
    id("love.forte.plugin.suspend-transform")
}

group = "id.walt.crypto"

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

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

kotlin {
    jvmToolchain(17)
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
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
        outputModuleName = "crypto"
        nodejs {
            generateTypeScriptDefinitions()
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
        binaries.library()
    }
    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    val ktor_version = "3.2.3"

    sourceSets {

        all {
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")

        }
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

                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
                implementation("org.kotlincrypto.hash:sha2")

                // Date
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

                // Cache
                implementation("io.github.reactivecircus.cache4k:cache4k:0.14.0")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

                // Logging
                implementation("io.github.oshai:kotlin-logging:7.0.5")

                implementation("${SuspendTransPluginConstants.ANNOTATION_GROUP}:${SuspendTransPluginConstants.ANNOTATION_NAME}:${SuspendTransPluginConstants.ANNOTATION_VERSION}")


                implementation(project.dependencies.platform("org.kotlincrypto.macs:bom:0.6.1"))
                implementation("org.kotlincrypto.macs:hmac-sha2")

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("com.google.crypto.tink:tink:1.16.0") // for JOSE using Ed25519

                implementation("org.bouncycastle:bcprov-lts8on:2.73.7") // for secp256k1 (which was removed with Java 17)
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.7") // PEM import

                // Ktor client
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.1")

                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")

            }
        }
        val jvmTest by getting {
            dependencies {
                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.16")

                // Test
                implementation(kotlin("test"))

                implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
            }
        }
        val jsMain by getting {
            dependencies {
                // JOSE
                implementation(npm("jose", "5.2.3"))

            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        if (enableIosBuild) {
            val iosArm64Main by getting
            val iosSimulatorArm64Main by getting

            val iosMain by creating {
                dependsOn(commonMain)
                iosArm64Main.dependsOn(this)
                iosSimulatorArm64Main.dependsOn(this)
                dependencies {
                    implementation(project(":waltid-libraries:crypto:waltid-target-ios"))
                }
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
                name.set("walt.id crypto")
                description.set("walt.id crypto")
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
            val releasesRepoUrl = uri("https://maven.waltid.dev/releases")
            val snapshotsRepoUrl = uri("https://maven.waltid.dev/snapshots")
            url = uri(
                if (version.toString().endsWith("SNAPSHOT")
                ) snapshotsRepoUrl else releasesRepoUrl
            )

            val envUsername = System.getenv("MAVEN_USERNAME")
            val envPassword = System.getenv("MAVEN_PASSWORD")

            val usernameFile = File("$rootDir/secret_maven_username.txt")
            val passwordFile = File("$rootDir/secret_maven_password.txt")

            val secretMavenUsername = envUsername ?: usernameFile.let {
                if (it.isFile) it.readLines().first() else ""
            }
            val secretMavenPassword = envPassword ?: passwordFile.let {
                if (it.isFile) it.readLines().first() else ""
            }
            credentials {
                username = secretMavenUsername
                password = secretMavenPassword
            }
        }

    }
}
