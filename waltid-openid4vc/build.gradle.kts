plugins {
    kotlin("multiplatform") version "1.9.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.21"
    id("dev.petuska.npm.publish") version "3.4.1"
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt"

repositories {
    mavenCentral()
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.multiformats")
        }
    }
    maven("https://maven.walt.id/repository/waltid-ssi-kit/") {
        content {
            includeGroup("id.walt")
        }
    }
    maven("https://repo.danubetech.com/repository/maven-public/")
    maven("https://maven.walt.id/repository/waltid/") {
        content {
            includeGroup("id.walt")
            includeGroup("id.walt.servicematrix")
            //includeGroup("info.weboftrust")
            includeGroup("decentralized-identity")
            //includeGroup("com.danubetech")
        }
    }
    mavenLocal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvm {
        jvmToolchain(8)
        withJava()
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
        nodejs() {
            generateTypeScriptDefinitions()
        }
        binaries.library()
    }
//    val hostOs = System.getProperty("os.name")
//    val isMingwX64 = hostOs.startsWith("Windows")
//    val nativeTarget = when {
//        hostOs == "Mac OS X" -> macosX64("native")
//        hostOs == "Linux" -> linuxX64("native")
//        isMingwX64 -> mingwX64("native")
//        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
//    }
    val ktor_version = "2.3.6"
    val HOPLITE_VERSION = "2.8.0.RC3"

    val kryptoVersion = "4.0.10"

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("io.ktor:ktor-http:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation(project(":waltid-sdjwt"))
                implementation("app.softwork:kotlinx-uuid-core:0.0.22")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":waltid-crypto"))
                implementation(project(":waltid-did"))
                implementation(project(":waltid-verifiable-credentials"))
                implementation("io.kotest:kotest-assertions-core:5.7.2")

                implementation("io.kotest:kotest-assertions-json:5.7.2")

                implementation ("io.github.microutils:kotlin-logging:1.12.5")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:$ktor_version")
            }
        }
        val jvmTest by getting {
            dependencies {
                //implementation("io.mockk:mockk:1.13.2")
                implementation("com.nimbusds:nimbus-jose-jwt:9.37.1")
                implementation("io.kotest:kotest-runner-junit5:5.7.2")
                implementation("io.kotest:kotest-assertions-core:5.7.2")
                implementation("io.kotest:kotest-assertions-json:5.7.2")
                implementation("com.google.crypto.tink:tink:1.12.0") // for JOSE using Ed25519
                // Multibase
                implementation("com.github.multiformats:java-multibase:v1.1.1")
                // TODO: current version implementation("id.walt:waltid-ssikit:1.2311131043.0")
                //implementation("id.walt:waltid-ssikit:1.JWTTYP") {
                //    exclude("waltid-sd-jwt-jvm")
                //    exclude(module = "waltid-sd-jwt-jvm")
                //}
                implementation("org.bouncycastle:bcprov-jdk18on:1.77") // for secp256k1 (which was removed with Java 17)
                implementation("org.bouncycastle:bcpkix-jdk18on:1.77") // PEM import
                implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

                implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-java:$ktor_version")
                implementation("io.ktor:ktor-client-auth:$ktor_version")
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")
                implementation("com.sksamuel.hoplite:hoplite-core:2.7.5")
                implementation("com.sksamuel.hoplite:hoplite-yaml:2.7.5")
                implementation("com.sksamuel.hoplite:hoplite-hikaricp:2.7.5")
                implementation("org.yaml:snakeyaml:2.2")
                implementation("org.xerial:sqlite-jdbc:3.44.1.0")

                // Config
                implementation("com.sksamuel.hoplite:hoplite-core:${HOPLITE_VERSION}")
                implementation("com.sksamuel.hoplite:hoplite-hocon:${HOPLITE_VERSION}")
                // https://mvnrepository.com/artifact/com.github.jnr/jnr-ffi
                implementation("com.github.jnr:jnr-ffi:2.2.15")
                // https://mvnrepository.com/artifact/com.github.ben-manes.caffeine/caffeine
                implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
// https://mvnrepository.com/artifact/net.java.dev.jna/jna
                implementation("net.java.dev.jna:jna:5.13.0")
// https://mvnrepository.com/artifact/com.goterl/lazysodium-java
                implementation("com.goterl:lazysodium-java:5.1.4")
// https://mvnrepository.com/artifact/com.goterl/resource-loader
                implementation("com.goterl:resource-loader:2.0.2")
// https://mvnrepository.com/artifact/com.beust/klaxon
                implementation("com.beust:klaxon:5.6")
                implementation("com.soywiz.korlibs.krypto:krypto:$kryptoVersion")
                // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-slf4j
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.7.3")
// https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")

            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jose", "~4.14.4"))
            }
        }
        val jsTest by getting {

        }
//        val nativeMain by getting
//        val nativeTest by getting
    }

    publishing {
        repositories {
            val envUsername = System.getenv("MAVEN_USERNAME")
            val envPassword = System.getenv("MAVEN_PASSWORD")
            val usernameFile = File("secret_maven_username.txt")
            val passwordFile = File("secret_maven_password.txt")
            val secretMavenUsername = envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" }
            val secretMavenPassword = envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }
            val hasMavenAuth = secretMavenUsername.isNotEmpty() && secretMavenPassword.isNotEmpty()
            if (hasMavenAuth) {
                maven {
                    url = uri("https://maven.walt.id/repository/waltid/")
                    credentials {
                        username = secretMavenUsername
                        password = secretMavenPassword
                    }
                }
            }
        }
    }
}

npmPublish {
    registries {
        val envToken = System.getenv("NPM_TOKEN")
        val npmTokenFile = File("secret_npm_token.txt")
        val secretNpmToken = envToken ?: npmTokenFile.let { if (it.isFile) it.readLines().first() else "" }
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
