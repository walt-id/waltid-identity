fun getSetting(name: String) = providers.gradleProperty(name).orNull.toBoolean()
val enableAndroidBuild = getSetting("enableAndroidBuild")
val enableIosBuild = getSetting("enableIosBuild")

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt.wallet"

repositories {
    mavenCentral()
    maven("https://maven.waltid.dev/snapshots")
}

kotlin {
    jvmToolchain(21)

    jvm()
    js(IR) {
        nodejs()
        browser()
    }
    if (enableIosBuild) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val ktor_version = "3.1.2"

        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

                implementation("io.github.oshai:kotlin-logging:7.0.5")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

                implementation(project(":waltid-libraries:crypto:waltid-crypto"))
                implementation(project(":waltid-libraries:waltid-did"))

                implementation(project(":waltid-libraries:protocols:waltid-openid4vc"))
                implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
                implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
                implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))

                // Ktor client
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-serialization:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
                implementation("io.ktor:ktor-client-json:$ktor_version")
                implementation("io.ktor:ktor-client-logging:$ktor_version")

                // Bouncy Castle
                implementation("org.bouncycastle:bcprov-lts8on:2.73.7")
                implementation("org.bouncycastle:bcpkix-lts8on:2.73.7")

                // Problematic libraries:
                implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")
                implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Ktor client
                implementation("io.ktor:ktor-client-okhttp-jvm:$ktor_version")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.slf4j:slf4j-simple:2.0.16")
            }
        }
    }
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id wallet core")
                description.set("walt.id Kotlin/Java wallet core library")
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
