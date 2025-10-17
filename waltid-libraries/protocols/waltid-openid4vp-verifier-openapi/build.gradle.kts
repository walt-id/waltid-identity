plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

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

dependencies {
    // Walt.id
    api(project(":waltid-services:waltid-service-commons"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp-verifier"))
    implementation(project(":waltid-libraries:credentials:waltid-dcql"))

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id Verifier SDK - OpenID4VP version - OpenAPI documentation blocks")
                description.set("walt.id Kotlin/Java Verifier for OpenID4VP - OpenAPI documentation blocks")
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
