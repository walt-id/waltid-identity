import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.petuska.npm.publish") version "3.5.3"
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

object Versions {
    const val KTOR_VERSION = "3.2.2" // also change 1 plugin
    const val COROUTINES_VERSION = "1.10.2"
    const val HOPLITE_VERSION = "2.9.0"
}

group = "id.walt.openid4vci"

repositories {
    mavenCentral()
    maven("https://maven.waltid.dev/releases") {
        content {
            includeGroup("id.walt")
        }
    }
    maven("https://maven.waltid.dev/snapshots")
    mavenLocal()
}

val targetVersion = JavaVersion.VERSION_1_8
val toolingRuntime = JavaVersion.VERSION_21

java {
    sourceCompatibility = targetVersion
    targetCompatibility = targetVersion
}

tasks.withType(KotlinCompile::class.java) {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(targetVersion.toString()))
    }
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

    jvmToolchain(toolingRuntime.majorVersion.toInt())
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        nodejs {
            generateTypeScriptDefinitions()
            testTask {
                useMocha {
                    timeout = "10000"
                }
            }
        }
        binaries.library()
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {

        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("io.ktor:ktor-http:${Versions.KTOR_VERSION}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")
                implementation("org.jetbrains.kotlinx:atomicfu:0.24.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES_VERSION}")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:${Versions.KTOR_VERSION}")
                implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
                implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jose", "5.10.0"))
                implementation("io.ktor:ktor-client-js:${Versions.KTOR_VERSION}")
            }
        }
        val jsTest by getting {

        }
//        val nativeMain by getting
//        val nativeTest by getting
        // Add for native: implementation("io.ktor:ktor-client-cio:$ktor_version")

        if (enableIosBuild) {
            val iosArm64Main by getting
            val iosSimulatorArm64Main by getting

            val iosMain by creating {
                dependsOn(commonMain)
                iosArm64Main.dependsOn(this)
                iosSimulatorArm64Main.dependsOn(this)
                dependencies {
                    implementation("io.ktor:ktor-client-darwin:${Versions.KTOR_VERSION}")
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
    }
}

tasks.named("jsBrowserTest") {
    enabled = false
}

npmPublish {
    registries {
        val envToken = System.getenv("NPM_TOKEN")
        val npmTokenFile = File("secret_npm_token.txt")
        val secretNpmToken =
            envToken ?: npmTokenFile.let { if (it.isFile) it.readLines().first() else "" }
        val hasNPMToken = secretNpmToken.isNotEmpty()
        val isReleaseBuild = Regex("\\d+.\\d+.\\d+").matches(version.get())
        if (isReleaseBuild && hasNPMToken) {
            println("-- RELEASE BUILD & NPM TOKEN --")
            readme.set(File("README.md"))
            register("npmjs") {
                uri.set(uri("https://registry.npmjs.org"))
                authToken.set(secretNpmToken)
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id OpenID4VCI library")
                description.set("walt.id Kotlin/Java OpenID4VCI library")
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
                username = System.getenv("MAVEN_USERNAME")
                    ?: File("$rootDir/secret_maven_username.txt").let { if (it.isFile) it.readLines().first() else "" }
                password = System.getenv("MAVEN_PASSWORD")
                    ?: File("$rootDir/secret_maven_password.txt").let { if (it.isFile) it.readLines().first() else "" }
            }
        }
    }
}
