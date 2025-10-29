plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.petuska.npm.publish") version "3.5.3"
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt.protocols"

repositories {
    mavenCentral()
    maven("https://maven.waltid.dev/releases") {
        content { includeGroup("id.walt") }
    }
    maven("https://maven.waltid.dev/snapshots")
    mavenLocal()
}

fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

kotlin {
    jvm()

    js(IR) {
        nodejs {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }

    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }
    val ktor_version = "3.2.2"

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

                // HTTP
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

                // Logging
                implementation("io.github.oshai:kotlin-logging:7.0.13")

                // Kotlinx
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("app.softwork:kotlinx-uuid-core:0.1.6")

                // JSON
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

                // Crypto
                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.8.0"))
                implementation("org.kotlincrypto.hash:sha2")

                // Temp
                // Bouncy Castle
                implementation("org.bouncycastle:bcprov-lts8on:2.73.8")
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.8")
                // JOSE
                implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")

                /*
                 * walt.id:
                 */
                implementation(project(":waltid-libraries:protocols:waltid-openid4vp"))
                implementation(project(":waltid-libraries:credentials:waltid-dcql"))
                implementation(project(":waltid-libraries:credentials:waltid-verification-policies2"))
                implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
                implementation(project(":waltid-libraries:web:waltid-ktor-notifications-core"))
                implementation(project(":waltid-libraries:waltid-did"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val jvmTest by getting {
            dependencies {
                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.16")
            }
        }
    }
}

/* -- Publishing -- */

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id OpenID4VP Client ID Prefix parsing")
                description.set("walt.id Kotlin/Java library for parsing OpenID4VP Client ID Prefixes")
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
