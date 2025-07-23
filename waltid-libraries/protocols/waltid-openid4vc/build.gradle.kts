import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.petuska.npm.publish") version "3.5.3"
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt.openid4vc"

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
        }
        binaries.library()
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    val ktor_version = "3.1.2"

    sourceSets {

        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

                // HTTP
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

                implementation("io.github.oshai:kotlin-logging:7.0.5")

                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

                // walt.id
                implementation(project(":waltid-libraries:crypto:waltid-crypto"))
                implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
                implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
                implementation(project(":waltid-libraries:credentials:waltid-verification-policies"))
                implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
                implementation(project(":waltid-libraries:waltid-did"))

                // -- Multiplatform --
                // Multiplatform / Uuid
                implementation("app.softwork:kotlinx-uuid-core:0.1.4")

                // Multiplatform / Date & time
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

                // Multiplatform / Hashes
                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.6.1"))
                implementation("org.kotlincrypto.hash:sha2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":waltid-libraries:crypto:waltid-crypto"))
                implementation(project(":waltid-libraries:waltid-did"))
                implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")
                implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
                implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")
                implementation("io.kotest:kotest-runner-junit5:5.9.1")
                implementation("io.kotest:kotest-assertions-core:5.9.1")
                implementation("io.kotest:kotest-assertions-json:5.9.1")
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
                implementation("com.google.crypto.tink:tink:1.16.0") // for JOSE using Ed25519
                implementation("org.bouncycastle:bcprov-lts8on:2.73.7") // for secp256k1 (which was removed with Java 17)
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.7") // PEM import

                implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-auth:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.10.1")
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")

                implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
                implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")

                implementation("org.slf4j:slf4j-simple:2.0.16")
            }
        }
        val jsMain by getting {
            dependencies {
                // implementation(npm("jose", "~4.14.4"))
                implementation(npm("jose", "5.2.3"))
                implementation("io.ktor:ktor-client-js:$ktor_version")
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
                    implementation("io.ktor:ktor-client-darwin:$ktor_version")
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
                name.set("walt.id OpenId4VC library")
                description.set("walt.id Kotlin/Java OpenId4VC library")
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
