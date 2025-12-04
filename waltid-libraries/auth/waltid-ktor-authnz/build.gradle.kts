import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("jvm")
    kotlin("plugin.power-assert")
    kotlin("plugin.serialization")
    id("io.ktor.plugin") version "3.2.2"
    id("maven-publish")

    application

    id("com.github.ben-manes.versions")
}

group = "id.walt"

application {
    mainClass.set("id.walt.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    // Auth methods
    // Core Web3j library
    implementation("org.web3j:core:4.14.0") // (5.0.0 is an invalid version!)

    // Optional: Web3j utils
    implementation("org.web3j:utils:4.14.0") // (5.0.0 is an invalid version!)


    // RADIUS
    implementation("org.aaa4j.radius:aaa4j-radius-client:0.4.0")

    // LDAP
    implementation("org.apache.directory.api:apache-ldap-api:2.1.7") {
        exclude("org.apache.mina:mina-core") // Manually updated due to security CVE
        exclude("org.apache.commons:commons-lang3") // Manually updated due to security CVE
    }
    implementation("org.apache.mina:mina-core:2.2.4")
    implementation("org.apache.commons:commons-lang3:3.19.0")

    // TOTP/HOTP
    implementation("com.atlassian:onetime:2.1.2")

    // JWT
    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-services:waltid-service-commons"))
    implementation("com.nimbusds:nimbus-jose-jwt:10.6")

    // Cryptography
    /*implementation(platform("dev.whyoleg.cryptography:cryptography-bom:0.4.0"))
    implementation("dev.whyoleg.cryptography:cryptography-core")
    implementation("dev.whyoleg.cryptography:cryptography-provider-jdk")*/
    implementation("com.password4j:password4j:1.8.4")
    implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.8.0"))
    implementation("org.kotlincrypto.hash:sha2")
    implementation("org.kotlincrypto.random:crypto-rand:0.6.0")

    // Ktor server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    //implementation("io.ktor:ktor-server-auth-ldap")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-apache")
    implementation("io.ktor:ktor-server-sessions")
    implementation("io.ktor:ktor-server-auto-head-response")
    implementation("io.ktor:ktor-server-double-receive")
    implementation("io.ktor:ktor-server-webjars")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-server-forwarded-header")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-html-builder")

    // Ktor client
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")

    // Ktor shared
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    // Ktor server external
    implementation("io.github.smiley4:ktor-openapi:5.3.0")
    implementation("io.github.smiley4:ktor-swagger-ui:5.3.0")
    implementation("io.github.smiley4:ktor-redoc:5.3.0")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

    // Logging
    implementation("io.klogging:klogging:0.11.6")
    implementation("io.klogging:slf4j-klogging:0.11.6")

    // Redis
    //implementation("eu.vendeli:rethis:0.3.3")
    implementation("io.github.domgew:kedis:0.0.11")

    /* --- Testing --- */
    testImplementation("io.ktor:ktor-client-logging")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // Ktor
    testImplementation("io.ktor:ktor-server-cio")
    testImplementation("io.ktor:ktor-server-test-host")

    // Kotlin
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id ktor-authnz")
                description.set(
                    """
                    Kotlin/Java library for AuthNZ
                    """.trimIndent()
                )
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

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    includedSourceSets = listOf("test")
    functions = listOf(
        // kotlin.test
        "kotlin.assert", "kotlin.test.assertTrue", "kotlin.test.assertEquals", "kotlin.test.assertNull",

        // checks
        "kotlin.require", "kotlin.check"
    )
}
