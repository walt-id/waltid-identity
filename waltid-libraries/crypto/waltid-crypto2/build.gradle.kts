plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("com.github.ben-manes.versions")
}

group = "id.walt.crypto"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}

kotlin {
    jvmToolchain(17)

    jvm {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.okio)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.bcprov.lts8on)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.nimbus.jose.jwt)
                implementation(libs.junit.jupiter.api)
                runtimeOnly(libs.junit.jupiter.engine)
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id crypto2")
                description.set("Next generation cryptography primitives for walt.id")
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
                if (version.toString().endsWith("SNAPSHOT")) {
                    uri("https://maven.waltid.dev/snapshots")
                } else {
                    uri("https://maven.waltid.dev/releases")
                }
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