plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.ben-manes.versions")
    id("maven-publish")
}

group = "id.walt.crypto"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // AWS
    implementation("aws.sdk.kotlin:kms:1.4.22")

    // JOSE
    implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Test> {
    enabled = false
}


kotlin {
    jvmToolchain(17)
    sourceSets {
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Walt.id Crypto AWS")
                description.set("Walt.id Crypto AWS Integration")
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
